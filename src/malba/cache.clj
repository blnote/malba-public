;; Copyright 2023 blnote.
;; This file is part of MALBA.

(ns malba.cache
  "implements two kinds of in-memory-caches: 
   in file-mode the cache consists of two maps cites and cited-by whose keys are publication ids
   and whose values are sets of ids
   in database-mode the LMDB key value store is used."
  
  (:require [malba.database :as db]
            [malba.file-io :as f]
            [malba.logger :as l]
            [datalevin.core :as d]))

(import java.util.HashMap)

(defn sizes
  "calculate cache sizes for logging"
  [C] 
  (->> (map (fn [mode]
              (when-let [ca (C mode)]
                {mode (if (some? (C :store)) 
                        (->  (d/stat (C :store) (name mode)) :entries) 
                        (count ca))})) [:cites :cited-by :details])
       (into {})))

(defn- log
  "log cache sizes to UI"
  [C]
  (let [{:keys [cites cited-by details]} (sizes C)] 
    (if details
      (l/cache (format "(%d/%d/%d)" cites cited-by details))
      (l/cache (format "(%d/%d)" cites cited-by)))))

(defn init
  "initialize empty cache with optional database connection"
  [db]
  (doto
   (if db 
     (let [store (d/open-kv "cache")]
       {:cites (d/open-dbi store "cites")
        :cited-by (d/open-dbi store "cited-by")
        :details (d/open-dbi store "details")
        :store store
        :db db})
      
     {:cites (HashMap.)
      :cited-by (HashMap.)
      :details (HashMap.)
      }) (log)))

(defn from-file
  "initialize cache from text file"
  [^java.io.File file]
  (let [{:keys [cites cited-by]} (f/load-network file)]
    (doto {:cites cites
           :cited-by cited-by
           :network-file (if (= (class file) java.lang.String)
                           file
                           (.getName file))} (log))))

(defn- cache-missing!
  "cache missing keys from database. parameters are db (database config), a set of keys designating the caches to update and a set of ids. "
  [{:keys [db] :as C} mode ids]
  (if (or (nil? db) (empty? ids)) ;check for database connection
    {} 
    (let [new-entries  (if (= mode :details)
                         (db/fetch-details db ids)
                         (db/fetch-citations db mode ids))
          store (C :store)] 
      (l/status "")
      (d/transact-kv store (name mode) (->> new-entries
                                            (mapv (fn [[k v]] [:put k v]))))
      (log C))))

(defn get-val [C id mode]
  (if-let [store (C :store)]
    (d/get-value store (name mode) id)
    (get (C mode) id)))

(defn get-seq [C mode ids]
  (if-let [store (C :store)]
    (map (fn [id] [id (d/get-value store (name mode) id)]) ids)
    (map (fn [id] [id (get (C mode) id)]) ids)))


(defn cache!
  "caches citation information for a list of ids (only the ones missing in cache). mode can be :details :cites or :cited-by
   missing entries are fetched from db and saved to cache
   no missing entries are cached in file mode, that is if (nil? (C :db)).
   returns a lazy seq of pairs [id cache-value] for ids" 
  ([C mode ids]
   (if (empty? ids)
     {}
     (when-let [store (C :store)] ;cache only in db mode
       (->> ids 
            (remove #(d/get-value store (name mode) %))
            (into [])   
            (cache-missing! C mode))))))

(defn look-up
  ([C mode ids]
   (cache! C mode ids)
   (get-seq C mode ids)))



(defn known-ids
  "caches ids and returns those, for which at least one cache has a non-empty entry."
  [C ids] 
  (let [cites (->> (look-up C :cites ids)
                   (remove #(empty? (second %)))
                   (map first)
                   (into #{}))
        cited-by (->> (look-up C :cited-by ids)
                      (remove #(empty? (second %)))
                      (map first)
                      (into #{}))] 
    (->> (concat cites cited-by) distinct)))


(defn clear! [{:keys [store] :as C}]
  (when store
    (doseq [dbi ["cites" "cited-by" "details"]]
      (d/clear-dbi store dbi)))
  (log C)
  C)


(defn- cache-to-stream!
  [^java.io.ObjectOutputStream out ca]
  (.writeInt out (count ca))
  (doseq [[k v] ca]
    (.writeObject out k)
    (.writeInt out (count v))
    (doseq [s v] (.writeObject out s))))

(defn- cache-from-stream
  [^java.io.ObjectInputStream in]
  (let [n (.readInt in)]
    (loop [m (transient {})
           i 0]
      (if (= i n) (persistent! m)
          (recur (assoc! m (.readObject in)
                         (->> (repeatedly (.readInt in) #(.readObject in))
                              (into #{}))) (inc i))))))

(defn to-stream!
  "writes cache to stream"
  [^java.io.ObjectOutputStream out C]
  (if (C :store)
    (doto out
      (.writeBoolean true)
      (db/to-stream! (C :db)))
    (doto out
      (.writeBoolean false)
      (.writeObject (C :network-file))
      (cache-to-stream! (C :cites))
      (cache-to-stream! (C :cited-by)))))


(defn from-stream
  [^java.io.ObjectInputStream in]
  (doto
   (if (.readBoolean in)
     (init (db/from-stream in))
     {:network-file (.readObject in)
      :cites (cache-from-stream in)
      :cited-by (cache-from-stream in)})
    (log)))



