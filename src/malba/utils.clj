;; Copyright 2023 blnote.
;; This file is part of MALBA.

(ns malba.utils
  (:require [clojure.set :as set]
            [clojure.string :as string]
            [malba.algorithms.malba-algo :as malba-algo]
            [malba.algorithms.proto-algo :as a]
            [malba.cache :as c]
            [malba.gephi :as gephi]
            [malba.gui :as gui]
            [malba.logger :as l]))


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
  [state ^String version ^java.io.File file]
  (with-open [outp (java.io.ObjectOutputStream.
                    (java.io.FileOutputStream. file))]
    ;write state without algo and cache
    (.writeObject outp version)
    (.writeObject outp (-> state
                           (dissoc :cache)
                           (dissoc :algo)))
    (c/to-stream! outp (state :cache))
    (a/to-stream! (state :algo) outp)))


(defn load-session
  "load session from file"
  [^java.io.File file]
  (with-open [inp (java.io.ObjectInputStream. (java.io.FileInputStream. file))]
    (l/debug (.readObject inp)) ;version 
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
  "returns end-points to edges with at least one subgraph node
   only returns edges among surrounding for nodes that would be isolated"
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
  "assemble graph information (nodes, edges, details) to be sent to Gephi"
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

(defn without-algo-buttons
  "disable algorithm buttons and enable stop button when executing expr"
  [expr]
  (try
    (gui/invoke :enable-algo-btns false)
    (expr)
    (finally (gui/invoke :enable-algo-btns true))))

(defn show-results!
  "update gephi graph and graph stats labes in UI, optionally clears graph
  and resets view before"
  ([state] (show-results! state false))
  ([{:keys [algo cache]} clear]
   (when clear (gephi/clear-graph))
   (l/status "Updating Preview...")
   (let [subgraph (a/subgraph algo)
         surrounding (a/generate-surrounding algo)]
     (-> (assemble-graph-info cache subgraph surrounding)
         gephi/update-graph)
     (gui/invoke :log-graph-size (format "Subgraph: %d nodes. Surrounding: %d nodes."
                                         (count subgraph)
                                         (count surrounding)))
     (gui/invoke :log-parameters (a/get-params-as-string algo))
     (gui/invoke :reset-preview)
     (l/status "Preview updated."))))

(defn update-ui!
  "update user interface with current state values"
  [{:keys [seed-file cache seed] :as state}]
  (gui/invoke :set-initialized (and (some? seed) (some? cache)))
  (gui/invoke :set-seed seed-file)
  (when cache
    (when (cache :db) (gui/invoke :set-db-info (cache :db)))
    (when-let [nf (cache :network-file)] (gui/invoke :set-network-file nf)))
  (when-let [algo (state :algo)] (gui/invoke :set-params (a/get-params algo))))

(defn init-algo
  "initializes algorithm if seed is loaded and network is available (cache not nil).
   fetches parameters from gui"
  [{:keys [seed cache] :as state}]
  (if (and seed cache)
    (let [_ (l/status "Initializing...")
          valid-seeds (c/known-ids cache seed)]
      (when (empty? valid-seeds) (throw (Exception. "None of the seeds found in network!")))
      (l/text (format "Found %d of %d seeds in network." (count valid-seeds) (count seed)))
      (let [pa (gui/invoke :get-params)
            algo-name (pa :name)
            new-state (-> state
                          (assoc :algo (a/init (algo-from-name algo-name) cache valid-seeds))
                          (update :algo a/set-params pa)
                          (assoc :algo-name (pa :name)))]
        (l/text (format ">>> Algorithm %s initialized." algo-name))
        (gui/invoke :set-initialized true) ;activate algorithm control in ui 
        (show-results! new-state true)
        (l/status "Initialized.")
        new-state))
    (do
      (gui/invoke :set-initialized false)
      (dissoc state :algo))))
