;; Copyright 2023 blnote.
;; This file is part of MALBA.

(ns malba.database
  "interface function to postgres publication database called from cache if entries are missing."
  (:require
   [clojure.set :as set]
   [clojure.string :as string]
   [malba.logger :as l]
   [next.jdbc :as jdbc]
   [next.jdbc.prepare :as jdbc-p]
   [next.jdbc.connection :as connection]
   [next.jdbc.result-set :as jdbc-rs]))

(comment 
  (clojure.repl.deps/add-lib 'com.zaxxer/HikariCP))

#_(import (com.zaxxer.hikari HikariDataSource))

(defn- prepare-sql [db mode ids]  
  (let [stmt [(db (-> mode name (str "-sql") keyword)) 
              (into-array String ids)]]
    #_(l/debug (str stmt)) 
    stmt))

(defn- check-connection [ds]
  (.close (jdbc/get-connection ds)))

(defn connect
  "checks if connected, otherwise connect to database return structure including prepared sql statements"
  [conf]
  (if-let [ds (conf :ds)]
    (do (check-connection ds) conf)
    (let [ds (-> conf 
                 (assoc :jdbcUrl (string/join ["jdbc:postgresql://" (conf :url)])) 
                 (jdbc/get-datasource))]
      (check-connection ds)
      (l/debug "reconnected to db") 
      (assoc conf :ds ds)
      )))


(defn close! "remove datasource" [conf]
  (dissoc conf :ds))

(defn- add-detail-labels
  "add labels to detail structure whenever pubyear and author is available"
  [details]
  (into {} (map (fn [[id detail]]
                  (let [pubyear (detail :pubyear)
                        authors (detail :authors)]
                    (if (and pubyear authors)
                      [id (assoc detail :label
                                 (string/join [(first (string/split authors #",")) "(" pubyear ")"]))]
                      [id detail])))) details))

(defn- shortened-authors
  "given an string containing authors from db returns a map 
   with key :authors containing a shortened author string"
  [^String auts]
  (if-not auts
    {}
    (let [authors (string/split auts #",")
          shortened (if (<= (count authors) 4)
                      auts
                      (->> [(authors 0) (authors 1) (authors 2) "..." (last authors)]
                           (interpose ",")
                           (string/join)))]
      {:authors shortened})))



(defn- fetch-details-main
  "fetch publication info from items table given db configuration
   for keys in details map" 
  [{:keys [ds sql-timeout-in-seconds] :as db} details]
  (l/debug "fetching main details...") 
  (with-open [conn
              (jdbc/get-connection ds)] 
    (let [stmt (prepare-sql db :details (keys details)) 
          plan (jdbc/plan conn stmt
                          {:builder-fn jdbc-rs/as-unqualified-maps
                           :timeout sql-timeout-in-seconds})]
      (reduce (fn [res {:keys [item_id item_title pubyear source_title]}] 
                (update res item_id #(cond-> %
                                       item_title (assoc :title item_title)
                                       pubyear (assoc :pubyear (str pubyear))
                                       source_title (assoc :source_title source_title)))) details plan))))
(defn- fetch-details-authors
  "fetch author info from authors table) given db configuration
   for keys in details map (only for ids which have been found in items table (previous step))" 
  [{:keys [ds sql-timeout-in-seconds] :as db} details]
  (l/debug "fetching author infos...")
  (with-open [conn
              (jdbc/get-connection ds)]
    (let [stmt (prepare-sql db :authors (keys details))
          rs (jdbc/execute! conn stmt
                            {:builder-fn jdbc-rs/as-unqualified-maps
                             :timeout sql-timeout-in-seconds})]
      (reduce (fn [res {:keys [item_id auts]}] 
                (update res item_id merge (shortened-authors auts))) details rs))))


(defn- fetch-details-missing
  "try to obtain publication details from ref table for ids not found in items table (not all cited papers are in the database themselves)"
  [{:keys [ds sql-timeout-in-seconds]:as db} details]
  (l/debug "fetching missing publication details...")
  (with-open [conn
              (jdbc/get-connection ds)]
    (let [missing (->> details (filter #(empty? (val %))) (map key))
          aut-tf (fn [^java.sql.Array ref_auts]
                   (when-let [s (first (.getArray ref_auts))]
                     {:authors (string/replace s #"[\{\"'\} ]" "")}))]
      (if (empty? missing) details
          (let [stmt (prepare-sql db :details-from-refs missing)
                rs (jdbc/execute! conn stmt
                                  {:builder-fn jdbc-rs/as-unqualified-maps
                                   :timeout sql-timeout-in-seconds})]
            (reduce (fn [res {:keys [item_id_cited,ref_item_title,ref_source_title,ref_authors,ref_pubyear]}]
                      (assoc res item_id_cited (cond-> {}
                                                 ref_item_title (assoc :title ref_item_title)
                                                 ref_source_title (assoc :source_title ref_source_title)
                                                 ref_pubyear (assoc :pubyear (str ref_pubyear))
                                                 ref_authors (merge (aut-tf ref_authors))))) details rs))))))


(defn fetch-details
  "get publication details from database. parameters are database configuration and a set of ids. if id is not found in database, an empty map entry is created to prevent repeated database lookups."
  [db ids]
  (if (empty? ids) {}
      (let [db (connect db)]
        (l/debug (format "fetching details for %d publications" (count ids)))
        (->> ids
             (into {} (map (fn [id] [id {}])))
             (fetch-details-main db)
             (fetch-details-authors db)
             (fetch-details-missing db)
             (add-detail-labels)))))


(defn- process-citation-resultsets [plan mode]
  (l/debug "processing results...") 
  (reduce (fn [res {:keys [^String item_id_cited ^String item_id_citing]}]
            (if (and item_id_cited item_id_citing)
              (let [entry (if (= mode :cites)
                            {(.intern item_id_citing) #{(.intern item_id_cited)}}
                            {(.intern item_id_cited) #{(.intern item_id_citing)}})]
                (merge-with set/union res entry))
              res))
          {} plan))

(defn fetch-citations
  "get map of citation data for a set of ids from database using prepared statements. mode can either be :cites or :cited-by. throws IllegalArgumentException whenever size of ids > max-query-size."
  [{:keys [ds sql-timeout-in-seconds] :as db} mode ids] 
  (let [{:keys [max-query-size max-batch-size]} (connect db)
        n (count ids)]
    (l/debug "fetching citations") 
    (cond 
      (= n 0) {} ;nothing to do
      (> n max-query-size)
      (throw (new IllegalArgumentException (format "SQL larger than MAX-QUERY-SIZE: (%d > %d) " (count ids) max-query-size))) 
      :else 
      (let [;;add every id as key to avoid repeated lookups  
            C (into {} (map (fn [id] [id #{}])) ids)]
        (->> (partition-all (or max-batch-size 1000) ids)
             (pmap (fn [ids]
                     (let [stmt (prepare-sql db mode ids)]
                       (with-open [conn
                                   (jdbc/get-connection ds)]
                         (-> (jdbc/plan conn
                                        stmt  {:builder-fn jdbc-rs/as-unqualified-maps
                                               :timeout sql-timeout-in-seconds})
                             (process-citation-resultsets mode))))))
             (apply merge-with set/union C))))))


(defn to-stream! [^java.io.ObjectOutputStream out conf]
  (.writeObject out (-> conf (dissoc :ds) (dissoc :prepared-cites) (dissoc :prepared-cited-by))))

(defn from-stream [^java.io.ObjectInputStream in]
  (.readObject in))


