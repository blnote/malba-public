;; Copyright 2023 blnote.
;; This file is part of MALBA.

(ns malba.file-io
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.edn :as edn]))


(defn load-seed
  "load seed from text file containing a publication id in every line."
  [file]
  (let [line-format #"^[a-zA-Z0-9:]{1,50}$"]
    (with-open [rdr (io/reader file)]
      (->> (line-seq rdr)
           (filter #(re-matches line-format %))
           (map string/trim)
           doall
           set))))

(defn load-network
  "load citation info from text file with two columns containing the ids of the citing and cited paper serparated by whitespace or tab. Discards first line as header."
  [file]
  (let [line-format #"^[a-zA-Z0-9:]{1,50}[\s]+[a-zA-Z0-9:]{1,50}$"]
    (with-open [rdr (io/reader file)]
      (let [[cites cited-by]
            (->> (line-seq rdr)
                 ;rest discard file header
                 (filter #(re-matches line-format %))
                 (map #(string/split % #"[\s]+"))
                 (reduce (fn [[cites cited-by] [a b]]
                           [(assoc! cites a (conj (get cites a #{}) b))
                            (assoc! cited-by b (conj (get cited-by b #{}) a))])
                         [(transient {}) (transient {})])
                 (map persistent!))]
        {:cites cites
         :cited-by cited-by}))))

(defn load-config
  "loads config file and checks if it is a map that has been read,
   returns nil in case of error."
  [filename]
  (try 
    (let [config (-> filename slurp edn/read-string)]
      (if (map? config) config nil))
    (catch Exception _ nil)))
