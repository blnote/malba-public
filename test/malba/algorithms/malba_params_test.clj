(ns malba.algorithms.malba-params-test
  (:require
   [clojure.test :refer [deftest is]]
   [malba.algorithms.malba-params :refer [->Params]])
  )


(deftest loosen-tighten-test
  (let [pa (-> (->Params) .init)]
    (is (= (-> pa (.tighten :bc) (.loosen :bc) (.get-vars))
             (-> pa (.loosen :bc) (.tighten :bc) (.get-vars))))))

(deftest update-vars-test
  (let [pa (-> (->Params) .init)]
    (is  (thrown? IllegalArgumentException
                  (.update-vars pa {:bc 2})))
    (is  (thrown? IllegalArgumentException
                  (.update-vars pa {:bc -2})))
    (is  (thrown? IllegalArgumentException
                  (.update-vars pa {:dc-in 2})))
    (is  (thrown? IllegalArgumentException
                  (.update-vars pa {:dc-in -2})))
    (is  (thrown? IllegalArgumentException
                  (.update-vars pa {:dc-out -2}))) 
    
    (is  (thrown? IllegalArgumentException
                  (.update-vars pa {:max-subgraph-size -1000})))
    (is  (thrown? IllegalArgumentException
                  (.update-vars pa {:max-parents-of-shared-refs -1000})))
    (is (= pa (.update-vars pa (.get-vars pa))))))
