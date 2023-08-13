;; Copyright 2023 blnote.
;; This file is part of MALBA.

(ns malba.cache
  (:require [malba.database :as db]
            [malba.file-io :as f]
            [malba.logger :as l]))


(import java.lang.ref.SoftReference
        java.util.HashMap)
;based on internet the setting -XX:SoftRefLRUPolicyMSPerMB=0
;is needed for the jvm to free all softreferences before 
; throwing an out of memory error


(defn- soft?
  "true if in soft cache mode (database)"
  [C]
  (some? (C :db)))

(defn sizes
  "calculate cache sizes for logging"
  [C]
  (let [soft (soft? C)]
    (->> (map (fn [mode]
                (when-let [ca (C mode)]
                  {mode (if soft (.size ca) (count ca))})) [:cites :cited-by :details])
         (into {}))))

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
   {:cites (HashMap.)
    :cited-by (HashMap.)
    :details (HashMap.)
    :db db} (log)))

(defn from-file
  "initialize cache from text file"
  [file]
  (let [{:keys [cites cited-by]} (f/load-network file)]
    (doto {:cites cites
           :cited-by cited-by
           :network-file (if (= (class file) java.lang.String)
                           file
                           (.getName file))} (log))))

(defn- get-soft
  "returns entry when in soft cache, otherwise nil.
   deletes entry if soft-reference has been cleared"
  [ca id]
  (when-let [ref (.get ca id)]
    (if-let [el (.get ref)]
      el
      (do (.remove ca id) nil))))

(defn- put-soft
  "write key and value to cache"
  [ca k v]
  (.put ca k (SoftReference. v)))

(defn- details-to-stream! 
  [out ca]
(.writeInt out (.size ca))
(doseq [entry ca]
  (let [id (.getKey entry)
        v (.get (.getValue entry))]
    (if (nil? v) (.writeObject out nil) 
        (do
          (.writeObject out id)
          (.writeObject out v))))))

(defn- details-from-stream
  [in]
  (let [n (.readInt in)
        hMap (HashMap.)]
    (doseq [_ (range n)] (when-let [id (.readObject in)]
                           (put-soft hMap (.intern id) (.readObject in))))
    hMap))


(defn- soft-cache-to-stream!
  [out ca]
  (.writeInt out (.size ca))
  (doseq [entry ca]
    (let [id (.getKey entry)
          v (.get (.getValue entry))]
      (if (nil? v) (.writeObject out nil)
          (do 
            (.writeObject out id)
            (.writeInt out (count v))
            (doseq [s v] (.writeObject out s)))))))

(defn- soft-cache-from-stream
  [in]
  (let [n (.readInt in)
        hMap (HashMap.)]
    (doseq [_ (range n)] (when-let [id (.readObject in)]
                               (.put hMap (.intern id) 
                                     (SoftReference. 
                                      (->> (repeatedly (.readInt in) #(.intern (.readObject in)))
                                           (into #{}))))))
    hMap))

(defn- cache-to-stream!
  [out ca]
  (.writeInt out (count ca))
  (doseq [[k v] ca]
    (doto out 
      (.writeObject k)
      (.writeInt (count v))
      (#(doseq [s v] (.writeObject % s))))))

(defn- cache-from-stream
  [in]
  (let [n (.readInt in )] 
    (loop [m (transient {})
           i 0]
      (if (= i n) (persistent! m)
          (recur (assoc! m (.readObject in)
                         (->> (repeatedly (.readInt in) #(.readObject in)) 
                              (into #{}))) (inc i))))))


(defn to-stream!
  "writes cache to stream"
  [out C]
  (if (soft? C)
    (doto out
      (.writeBoolean true)
      (db/to-stream! (C :db))
      (soft-cache-to-stream! (C :cites))
      (soft-cache-to-stream! (C :cited-by))
      (details-to-stream! (C :details)))
    (doto out
      (.writeBoolean false)
      (.writeObject (C :network-file))
      (cache-to-stream! (C :cites))
      (cache-to-stream! (C :cited-by)))))


(defn from-stream
  [in] 
  (doto 
   (if (.readBoolean in)
     {:db (db/from-stream in) 
      :cites (soft-cache-from-stream in)
      :cited-by (soft-cache-from-stream in)
      :details (details-from-stream in)} 
     {:network-file (.readObject in)
      :cites (cache-from-stream in)
      :cited-by (cache-from-stream in)})
    (log)
    ))


(defn clean-soft-cache [ca]
  (when (-> ca (.values) (.removeIf
                          (reify java.util.function.Predicate
                            (test [_ arg]
                              (nil? (.get arg))))))
    (tap> "cleaned soft cache elements!")))

(defn- to-cache
  "write elements (given as map) to cache"
  [ca elems]
  (if (instance? java.util.HashMap ca)
    (do
      (doseq [[k v] elems]
        (.put ca k (SoftReference. v)))
      (clean-soft-cache ca))
    (swap! ca merge elems)))

(defn- cache-missing!
  "cache missing keys from database. parameters are db (database config), a set of keys designating the caches to update and a set of ids. "
  [{:keys [db] :as C} mode ids]
  (if (or (nil? db) (empty? ids)) ;check for database connection
    {}
    (let [n (count ids)
          _ (when (> n 100) (l/status (format "Loading %d entries from db..." n)))
          new-entries  (if (= mode :details)
                         (db/fetch-details (C :db) ids)
                         (db/fetch-citations db mode ids))
          _ (when (> n 100) (l/status (format "Loading %d entries from db done." n)))]
      (to-cache (C mode) new-entries)
      (log C)
      new-entries)))


(defn look-up-details [C ids]
  (if-let [ca (C :details)]
    (let [[inside missing]
          (reduce (fn [[inside missing] id]
                    (if-let [r (get-soft ca id)]
                      [(conj! inside [id r]) missing]
                      [inside (conj missing id)])) [(transient {}) ()]  ids)] 
      (into (persistent! inside) (cache-missing! C :details missing)))
    {}))

(defn look-up
  "returns citation information from cache C for a list of ids. mode can be :cites or :cited-by or empty,
   in which case a list of information of both caches is returned.
   missing entries are saved to :soft (default) or :hard cache according to save-to parameter.
   no missing entries are cached in file mode, that is if (nil? (C :db))."
  ([C ids] [(look-up C :cites ids) (look-up C :cited-by ids)])
  ([C mode ids]
   (if (empty? ids)
     {}
     (if-not (C :db)
       (let [ca (C mode)]
         (persistent! (reduce (fn [res id]
                                (if-let [r (get ca id)]
                                  (conj! res [id r])
                                  res)) (transient {}) ids)))
       (let [ca (C mode)
             [inside missing]
             (reduce (fn [[inside missing] id]
                       (if-let [r (get-soft ca id)]
                         [(conj! inside [id r]) missing]
                         [inside (conj missing id)])) [(transient {}) ()]  ids)] 
         (into (persistent! inside) (cache-missing! C mode missing)))))))

(defn known-ids
  "caches ids and returns those, for which at least one cache has a non-empty entry."
  [C ids]
  (let [cites (look-up C :cites ids)
        cited-by (look-up C :cited-by ids)]
    (->> ids
         (remove #(and (empty? (cites %))
                       (empty? (cited-by %)))))))






