;; Copyright 2023 blnote.
;; This file is part of MALBA.

(ns malba.utils
  (:require
   [clojure.string :as string]
   [clojure.set :as set] 
   [malba.algorithms.proto-algo :as a]
   [malba.cache :as c]
   [malba.algorithms.malba-algo :as malba-algo]))


(import (java.awt.datatransfer StringSelection)
        (java.awt Toolkit))


(defn algo-from-name
  "creates algorithm instance from name"
  [^String n]
  (condp = n
    "MALBA" (malba-algo/->MalbaAlgo)
    (throw (Error. (format "Algorithm %s not yet implemented." n)))))

(defn save-session!
  "save session to file"
  [state file]
  (with-open [outp (java.io.ObjectOutputStream.
                    (java.io.FileOutputStream. file))]
    ;write state without algo and cache
    (.writeObject outp "0.8B")
    (.writeObject outp (-> state
                           (dissoc :cache)
                           (dissoc :algo)))
    (c/to-stream! outp (state :cache))
    (a/to-stream! (state :algo) outp)))


(defn load-session
  "load session from file"
  [file]
  (with-open [inp (java.io.ObjectInputStream. (java.io.FileInputStream. file))]
    (tap> (.readObject inp)) ;version 
    (let [state (.readObject inp)
          cache (c/from-stream inp)
          algo  (-> (state :algo-name) ;generate class from serialized map 
                    (algo-from-name)
                    (a/from-stream inp cache))]
      (-> state
          (assoc :cache cache)
          (assoc :algo algo)))))

(defn copy-to-clipboard [^String s]
  (when s
    (try
      (let [cb (.getSystemClipboard (Toolkit/getDefaultToolkit))
            ss (StringSelection. s)]
        (.setContents cb ss ss))
      (catch Exception _ (throw (Error. "Not able to copy to clipboard."))))))

(defn generate-detail-str
  "generate publication detail info string to display when hovering over node"
  [node-data]
  (when node-data
    (let [{:keys [id pubyear title authors source_title step]} node-data
          truncate #(subs % 0 (min (count %) 100))]
      (string/join ["ID: \t" id
                    (when title (format "\nTitle:\t%s" (truncate title)))
                    (when authors (format "\nAuthors:\t%s" authors))
                    (when pubyear (format "\nPub. Year:\t%s" pubyear))
                    (when source_title (format "\nSource Title:\t%s" (truncate source_title)))
                    "\nAdded in Step:\t" (str step)]))))

(defn filter-edges
  "returns end-points to edges with at least one subgraph node.
   only returns edges among surounding for nodes that would be isolated."
  [id subgraph surrounding cites all]
  (let [in-sur (contains? surrounding id)
        all-end-pts (filter all (cites id))
        end-pts (if in-sur
                  (filter subgraph all-end-pts)
                  all-end-pts)]
    (->> (if (empty? end-pts) all-end-pts end-pts)
         (map (fn [e] {:end e
                       :surrounding ;edge connects with surrounding
                       (or in-sur (contains? surrounding e))})))))

(defn assemble-graph-info
  "assemble graph information (nodes, edges, details) to be sent do gephi"
  [cache subgraph surrounding] 
  (let [subgraph-ids (into #{} (keys subgraph))
        surrounding-ids (into #{} (keys surrounding))
        all (set/union subgraph-ids surrounding-ids)
        details (c/look-up-details cache all)
        cites (c/look-up cache :cites all)]
    (->> all
         (map (fn [id]
                (let [in-sur (contains? surrounding id)]
                  {id {:details (details id) 
                       :step (if in-sur (surrounding id) (subgraph id))
                       :surrounding in-sur
                       :edges (filter-edges id subgraph-ids surrounding-ids cites all)}})))
         (into {}))))


(defn algo-changed?
  "true iff chosen algorithm in UI has changed since initialization"
  [state pa]
  (not (= (pa :name) (-> (state :algo) a/get-params (get :name)))))

