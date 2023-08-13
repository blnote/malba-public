;; Copyright 2023 blnote.
;; This file is part of MALBA.

(ns malba.database
  (:require [clojure.set :as set]
            [clojure.string :as string]
            [next.jdbc :as jdbc]
            [next.jdbc.prepare :as jdbc-p]
            [next.jdbc.result-set :as jdbc-rs]))

(defn- insert-ids
  "insert ids in sql string from config file by replacing IDLIST"
  [s ids]
  (string/replace s "IDLIST" ids))

(defn- sql-?-string
  "constructs string (?,?,...,?) with number of question-marks equal to size. used as parameters for prepared statements"
  [size]
  (->> (repeat size \?)
       (interpose \,)
       string/join
       (format "(%s)")))

(defn- sql-id-string
  "transform ids into string ('id1','id2',...) used in detail sql query."
  [ids]
  (->> (map (fn [id] (string/join ["'" id "'"])) ids)
       (interpose ",")
       (string/join)
       (format "(%s)")))

(defn connect
  "checks if connected, otherwise connect to database return structure including prepared sql statements"
  [conf]
  (if (conf :conn)
    conf
    (let [conf (assoc conf :jdbcUrl (string/join ["jdbc:postgresql://" (conf :url)]))
          conn (jdbc/get-connection conf)]
      (-> conf
          (assoc :conn conn)
          (assoc :prepared-cites
                 (mapv (fn [size]
                         (let [sql-str (insert-ids (conf :cites-sql) (sql-?-string size))]
                           (jdbc/prepare conn [sql-str]
                                         {:timeout (conf :sql-timeout-in-seconds)}))) (conf :batch-sizes)))
          (assoc :prepared-cited-by
                 (mapv (fn [size]
                         (let [sql-str (insert-ids (conf :cited-by-sql) (sql-?-string size))]
                           (jdbc/prepare conn [sql-str]
                                         {:timeout (conf :sql-timeout-in-seconds)}))) (conf :batch-sizes)))))))



(defn close! "close db connection if existent" [conf]
  (when conf
    (if-let [conn (conf :conn)]
      (do (.close conn)
          (dissoc conf :conn))
      conf)))

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
  [db details]
  (let [{:keys [conn details-sql sql-timeout-in-seconds]} db
        id-str (sql-id-string (keys details))
        sql (insert-ids details-sql id-str)
        rs (jdbc/execute! conn [sql]
                          {:builder-fn jdbc-rs/as-unqualified-maps
                           :time-out sql-timeout-in-seconds})]
    (reduce (fn [res {:keys [item_id item_title pubyear source_title]}]
              (update res item_id #(cond-> %
                                     item_title (assoc :title item_title)
                                     pubyear (assoc :pubyear (str pubyear))
                                     source_title (assoc :source_title source_title)))) details rs)))
(defn- fetch-details-authors
  "fetch author info from authors table given db configuration
   for keys in details map"
  [db details]
  (let [{:keys [conn authors-sql sql-timeout-in-seconds]} db
        id-str (sql-id-string (keys details))
        sql (insert-ids authors-sql id-str)
        rs (jdbc/execute! conn [sql]
                          {:builder-fn jdbc-rs/as-unqualified-maps
                           :time-out sql-timeout-in-seconds})]
    (reduce (fn [res {:keys [item_id auts]}]
              (update res item_id merge (shortened-authors auts))) details rs)))


(defn- fetch-details-missing
  "try to obtain publication details from ref table for ids not found in items table"
  [db details]
  (let [missing (->> details (filter #(empty? (val %))) (map key))
        aut-tf (fn [ref_auts]
                 (when-let [s (first (.getArray ref_auts))]
                   {:authors (string/replace s #"[\{\"'\} ]" "")}))]
    (if (empty? missing) details
        (let [{:keys [conn details-from-refs-sql sql-timeout-in-seconds]} db
              id-str (sql-id-string missing)
              sql (insert-ids details-from-refs-sql id-str)
              rs (jdbc/execute! conn [sql]
                                {:builder-fn jdbc-rs/as-unqualified-maps
                                 :time-out sql-timeout-in-seconds})]
          (reduce (fn [res {:keys [item_id_cited,ref_item_title,ref_source_title,ref_authors,ref_pubyear]}]
                    (assoc res item_id_cited (cond-> {}
                                               ref_item_title (assoc :title ref_item_title)
                                               ref_source_title (assoc :source_title ref_source_title)
                                               ref_pubyear (assoc :pubyear (str ref_pubyear))
                                               ref_authors (merge (aut-tf ref_authors))))) details rs)))))


(defn fetch-details
  "get publication details from database. parameters are database configuration and a set of ids. if id is not found in database, an empty map entry is created to prevent repeated database lookups."
  [db ids]
  (if (empty? ids) {}
      (let [db (connect db)]
        (->> ids
             (into {} (map (fn [id] [id {}])))
             (fetch-details-main db)
             (fetch-details-authors db)
             (fetch-details-missing db)
             (add-detail-labels)))))


(defn fetch-citations
  "get map of citation data for a set of ids from database using prepared statements. mode can either be :cites or :cited-by. throws IllegalArgumentException whenever size of ids > max-query-size."
  [db mode ids]
  (let [{:keys [max-query-size batch-sizes prepared-cites prepared-cited-by]} (connect db)]
    (if (> (count ids) max-query-size)
      (throw (new IllegalArgumentException (format "SQL larger than MAX-QUERY-SIZE: (%d > %d) " (count ids) max-query-size)))
      (loop [ids ids
             C (into {} (map (fn [id] [id #{}])) ids)] ;;add every id as key to avoid repeated lookups
        (if (empty? ids) C ;nothing to do
            (let [;find idx of smallest batch size > than number of ids to cache  
                  batch-idx (or (->> (map-indexed vector batch-sizes)
                                     (filter #(> (last %) (count ids)))
                                     ffirst)
                                (dec (count batch-sizes)))
                  batch-sz (batch-sizes batch-idx)
                  cur-ids (take batch-sz ids)
                ;prepare prepared statement corresponding to batch size
                  stmt (-> (if (= mode :cites)
                             (prepared-cites batch-idx)
                             (prepared-cited-by batch-idx))
                           (jdbc-p/set-parameters
                            (concat cur-ids
                                    (repeat (- batch-sz (count cur-ids)) ""))))

                  m (->> (jdbc/execute! stmt nil {:builder-fn jdbc-rs/as-unqualified-maps})
                         (filter #(and (% :item_id_cited) (% :item_id_citing)))
                         (reduce (fn [res {:keys [item_id_cited item_id_citing]}]
                                   (let [entry (if (= mode :cites)
                                                 {(.intern item_id_citing) #{(.intern item_id_cited)}}
                                                 {(.intern item_id_cited) #{(.intern item_id_citing)}})]
                                     (merge-with set/union res entry)))
                                 {}))]
              (recur (drop batch-sz ids) (merge-with set/union C m))))))))


(defn to-stream! [out conf]
  (.writeObject out (-> conf (dissoc :conn) (dissoc :prepared-cites) (dissoc :prepared-cited-by))))

(defn from-stream [in]
  (.readObject in))
