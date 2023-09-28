;; Copyright 2023 blnote.
;; This file is part of MALBA.

(ns malba.algorithms.malba-algo
  "implementation of MALBA algorithm"
  (:require [malba.algorithms.malba-params :as malba-params]
            [malba.algorithms.proto-algo :as proto-algo] 
            [malba.logger :as l]
            [malba.cache :as c :refer [look-up]]
            [malba.algorithms.proto-algo-params :as params]))

(defn- terminated? [state]
  (and (= (get state :added) 0) (not (get state :error))))

(defn- error? [state]
  (get state :error))

(defn- interrupted? [state]
  @(get state :interrupted))


(defn-  remove-from-map
  "remove multiple elements from map"
  [m elems]
  (apply dissoc m (seq elems)))

(defn- count-elems-in-map
  "count number of elements that are contained in map m." 
  ([m elements]
   (reduce (fn [res el]
             (if (m el) (inc res) res)) (long 0) elements)))

(defn- filter-parents
  "Given a map containg children and its set of parents, 
   removes children with too many parents (max-shared), concats all parents, 
   removes duplicates and nodes already in subgraph"
  [max-shared subgraph data]
  (into () (comp
            (remove (fn [[_ cb]]
                      (or (nil? cb) (empty? cb) (> (count cb) max-shared))))
            (map second)
            cat
            (remove subgraph)
            (distinct)) data))

(defn- add-nodes
  "add nodes that have been selected in algorithm steps to subgraph, cache new elements and update structures. work horse of algorithm, called at the end of every step."
  [{:keys [subgraph params C error steps] :as state} ids]
  (let [state (update state :steps inc)]
    (if (empty? ids) state
        (let [ids (remove subgraph ids) ;remove ids already in subgraph 
              new-subgraph (into subgraph (map (fn [id] [id steps])) ids)]
          (if (or error (empty? ids))
            state
            (if (> (count new-subgraph) (get params :max-subgraph-size))
              (assoc state :error :subgraph-too-large)
              (try (let [{:keys [dc-in bc dc-out min-refs 
                                 max-parents-of-shared-refs]} params 
                         children-mult (->> (look-up C :cites ids)
                                            vals
                                            (apply concat))
                         citing-sub-new (when dc-in
                                          (->> (look-up C :cited-by ids)
                                               (filter-parents max-parents-of-shared-refs new-subgraph)
                                               (look-up C :cites)))
                         citing-common-new (when bc
                                             (->>  (look-up C :cited-by (distinct children-mult))
                                                   (filter-parents max-parents-of-shared-refs new-subgraph)
                                                   (look-up C :cites)
                                                   (filter (fn [[_ ci]] (>= (count ci) min-refs)))))]
                     (cond-> state
                       true (assoc :subgraph new-subgraph)
                       true (update :added + (count ids)) 
                       dc-out (update :cited-by-sub #(merge-with + % (frequencies children-mult)))
                       bc (update :citing-common into citing-common-new)
                       bc (update :citing-common remove-from-map ids)
                       dc-in (update :citing-sub into citing-sub-new)
                       dc-in (update :citing-sub remove-from-map ids)))
                   (catch IllegalArgumentException _
                     (assoc state :error :sql-query-too-large)))))))))

(defn- step-dc-out
  "DCout step of algorithm. add elements that are cited by subgraph the required times"
  [{:keys [cited-by-sub params error] :as state}]
  (if (or error (nil? (get params :dc-out))) state
      (let [dc-out (get params :dc-out)]
        (->> cited-by-sub
             (filter (fn [[_ n-cited]] (>= n-cited dc-out)))
             (map first)
             (add-nodes state)))))


(defn- step-dc-in
  "DCin step of algorithm. add all elements of  citingSub  to subgraph, 
     whose fraction of references fullfills criteria"
  [{:keys [citing-sub subgraph params error] :as state}]
  (if (or error (nil? (get params :dc-in))) state
      (let [dc-in (get params :dc-in)]
        (->> citing-sub
             (filter (fn [[_ cites]]
                       (let [ci (count-elems-in-map subgraph cites)]
                         (and
                          (>= ci (get params :min-cited))
                          (>= (/ ci (count cites)) dc-in)))))
             (map first)
             (add-nodes state)))))

(defn- step-bc
  "BC step of algorithm. check each element in citing-common whether it fullfills requirement 
   concerning shared references, add to subgraph if true."
  [{:keys [citing-common cited-by-sub params error] :as state}]
  (if (or error (nil? (get params :bc))) state
      (let [bc (get params :bc)]
        (->> citing-common
             (filter (fn [[_ cites]]
                       (>= (/ (count-elems-in-map cited-by-sub cites) (count cites)) bc)))
             (map first)
             (add-nodes state)))))

(defn init
  "init algorithm with cache C and a set of seed ids and (default) parameters."
  ([C seed]
   (init C seed (params/init (malba-params/->Params))))
  ([C seed params]
   (let [state {:C C
                :params params
                :valid-seeds seed ;seeds found in database/file
                :cited-by-sub {} ;map id -> number of times cited for all papers cited by subgraph
                :citing-sub {} ;map id -> citations for all DCin candidates
                :citing-common {} ;map id -> citations for all BC candidates
                :subgraph {} ;map id -> step (step in which element has been added to subgraph) 
                :steps 0 ;(number of steps done overall (of three steps)) 
                :added 0 ;elements added during cycle. used to test if algorithm terminated
                :interrupted (atom false) ;atom used to stop algorithm during run or parameter search
                :error false ;true e.g. if subgraph grew too large (associated in add-nodes function)
                }]
     (add-nodes state seed))))

(defn step
  "performs a cycle of the malba algorithm."
  [state]
  (reset! (get state :interrupted) false)
  (let [new-state
        (-> state
            (assoc :error false)
            (assoc :added 0) ;elements added during cycle
            step-dc-out
            step-bc
            step-dc-in)]
    (l/text (format "Elements added in cycle: %d" (get new-state :added)))
    new-state))

(defn run
  "runs step of algorithm until terminated, failed or interrupted."
  ([state]
   (reset! (get state :interrupted) false)
   (loop [state (step state)]
     (if (or (interrupted? state) (terminated? state) (error? state))
       state
       (recur (step state))))))


(defn- run-loop
  "runs algorithm without logging and overhead, used in search"
  [state]
  (let [step-f (comp step-dc-in step-bc step-dc-out)]
    (loop [s state] 
      (let [ns (step-f (assoc s :added 0))] 
        (if
         (or (terminated? ns) (error? ns) (interrupted? ns)) ns (recur ns))))))


(defn- search-rec [{:keys [interrupted] :as state}]
  (reset! interrupted false)
  (loop [tried {}
         to-try (conj () (get state :params))]
    (if (or @interrupted (empty? to-try)) tried
        (let [p (first to-try)]
          (if (contains? tried p) (recur tried (rest to-try))
              (let [s (run-loop (assoc state :params p))
                    size (count (get s :subgraph))]
                (l/text (format "%s  subgraph: %s" (params/to-string p)
                                (if (terminated? s) (str size) "-"))) 
                (cond
                  (interrupted? s) tried
                  (terminated? s) (recur (assoc tried p size)
                                         (->> [(params/loosen p :dc-in)
                                               (params/loosen p :bc)
                                               (params/loosen p :dc-out)]
                                              (remove nil?)
                                              shuffle
                                              (into (rest to-try))))
                  (error? s)
                  (do (when (= (error? s) :sql-query-too-large)
                        (l/text "Warning: sql query too large!"))
                      (recur (assoc tried p -1)
                             (->> [(params/tighten p :dc-in)
                                   (params/tighten p :bc)
                                   (params/tighten p :dc-out)]
                                  (remove nil?)
                                  shuffle
                                  (into (rest to-try)))))
                  :else (throw (Error. "No matching clause found in recursive search!")))))))))

(defn search
  "search parameters leading largest subgraph < max-subgraph-size."
  [state]
  (let [tried (search-rec state)
        [mk mv] (reduce (fn [[mk mv] [k v]]
                          (if (> v mv) [k v] [mk mv])) [nil 0] tried)]
    {:params mk
     :size mv
     :interrupted @(get state :interrupted)}))


(defn generate-surrounding [{:keys [cited-by-sub citing-sub subgraph citing-common params steps]}]
  (let [{:keys [dc-out dc-in bc surrounding]} params
        add-dc-out (when dc-out
                     (let [ths (- dc-out (surrounding :dc-out))]
                       (->> cited-by-sub
                            (filter (fn [[_ n-cited]] (>= n-cited ths)))
                            (map first))))
        add-bc (when bc
                 (let [ths (- bc (surrounding :bc))]
                   (->> citing-common
                        (filter (fn [[_ cites]]
                                  (>= (/ (count-elems-in-map cited-by-sub cites) (count cites)) ths)))
                        (map first))))
        add-dc-in (when dc-in
                    (let [ths (- dc-in (surrounding :dc-in))]
                      (->> citing-sub
                           (filter (fn [[_ cites]]
                                     (let [ci (count-elems-in-map subgraph cites)]
                                       (and
                                        (>= ci (get params :min-cited))
                                        (>= (/ ci (count cites)) ths)))))
                           (map first))))]
    (->> (concat add-dc-out add-bc add-dc-in)
         (remove (into #{} (keys subgraph)))
         (remove nil?)
         (map (fn [id] [id (dec steps)]))
         (into {}))))

;;generating a record from all functions implementing the algorithm protocol

(defrecord MalbaAlgo []
  proto-algo/Algo
  (init [this cache seed] (merge this (init cache seed)))
  (step [this] (step this))
  (run [this] (run this))
  (search [this] (search this))

  (generate-surrounding [this] (generate-surrounding this)) 
  (subgraph [this] (get this :subgraph))

  (set-params [this params]
    (update this :params #(params/update-vars % params)))
  (get-params [this] (if-let [pa (get this :params)]
                       (params/get-vars pa)
                       (-> (malba-params/->Params) params/init params/get-vars)))
  (get-params-as-string [this]
    (if-let [pa (get this :params)]
      (params/to-string pa)
      (-> (malba-params/->Params) params/init params/to-string)))

  (terminated? [this] (terminated? this))
  (interrupted? [this] (interrupted? this))
  (error [this] (error? this))
  (steps [this] (quot (get this :steps) 3))
  (to-stream! [this out]
             (let [serialized (into {} (-> this 
                                           (dissoc :C) 
                                           (dissoc :interrupted) 
                                           (update :citing-common keys)
                                           (update :citing-sub keys)
                                           (update :params #(into {} %))))]
               (.writeObject  ^java.io.ObjectOutputStream out serialized)))
  (from-stream [this in cache]
               (-> (merge this (.readObject ^java.io.ObjectInputStream in) {:C cache
                                                 :interrupted (atom false)
                                                 :error false})
                   (update :params #(params/update-vars (malba-params/->Params) %)) 
                   (update :citing-common #(->> (c/look-up cache :cites %)))
                   (update :citing-sub #(->> (c/look-up cache :cites %))))))

