(ns malba.algorithms.malba-algo-test
  (:require
   [clojure.test :refer [deftest is]]
   [malba.algorithms.malba-algo :refer [init run search]]
   [malba.cache :as c]
   [malba.database :as db]
   [malba.file-io :as f]
   [malba.algorithms.malba-params]))

(defn- counts "returns map of sizes of algorithm structures"
  [state]
  (let [ks [:subgraph :cited-by-sub :citing-common :citing-sub]]
    (->> ks
         (map (fn [k] {k (count (get state k))}))
         (into {}))))

;parameters used for testing
(def params {:name  "MALBA"

                     ;;default values
                     :dc-out 8
                     :dc-in 0.9
                     :bc 0.9
                     :max-subgraph-size 2000


                     ;;boundaries (for parameter search)
                     :min-p {:dc-out 5
                             :dc-in 0.3
                             :bc 0.3}
                     :max-p {:dc-out 12
                             :dc-in 0.99
                             :bc 0.99}

                     ;;step sizes (for parameter search)
                     :step {:dc-out 1
                            :dc-in 0.05
                            :bc 0.05}

                     ;parameter to avoid candidate explosion described in email
                     :max-parents-of-shared-refs 100

                     ;;used in calculation of dcin as filter:
                     ;;only papers citing subgraph at least 'mincited' times are considered 
                     :min-cited 3

                     ;;used in calculation of bc, only papers with more than 'minrefs'
                     ;;references are considered
                     :min-refs 8

                     ;;values substracted from thresholds for construction of surrounding 
                     :surrounding {:dc-out 3
                                   :dc-in 0.2
                                   :bc 0.2}})

(deftest algorithm-test
  (let [params (merge (malba.algorithms.malba-params/->Params) params)
        C-file (c/from-file "test/data/networkWOS.txt")
        C-db (c/init (db/connect (-> "test/data/database-local.edn" slurp read-string)))
        seed (f/load-seed  "test/data/seedWOS.txt")
        seed-f (c/known-ids C-file seed)]
    (is (= 25 (count seed-f)))
    (let [state-f (-> (init C-file seed-f params))
          state-db (-> (init C-db seed-f params))
          cm (->> (keys state-f)
                  (remove #{:C :interrupted :params})
                  (map #(= (state-f %) (state-db %)))
                  (every? true?))]
      (is (= (counts state-f) (counts state-db)))

      (is (= (counts state-f) {:cited-by-sub 205
                               :citing-common 2067
                               :citing-sub 283
                               :subgraph 25}) "test inititialization of algorithm")
      (is (= (counts (run state-f)) {:cited-by-sub 238
                                     :citing-common 2329
                                     :citing-sub 449
                                     :subgraph 39}) "test if structures after running correct")
      (is (true? cm) "tests if algorithm structures of db and file mode are equal")
      (let [search-res  (search state-f)]
        (is  (= (search-res :size) 312) "check if search results in subgraph of correct size"))) 
    (db/close! (C-db :db))))