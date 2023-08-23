(ns malba.cache-test
  (:require
   [clojure.test :refer [deftest is]]
   [malba.cache :refer [from-file look-up init known-ids]]))

(import '[java.io FileNotFoundException])

(deftest from-file-test
  (is (thrown? FileNotFoundException (from-file "not existend")))
  (let [{:keys [cites cited-by] :as C} (from-file "test/data/networkWOS.txt")]
    (is (> (count cites) 0))
    (is (> (count cited-by) 0))
    (let [id "WOS:A1997WM73200001"
          children (look-up C :cites [id])
          child (first (children id))
          parents (look-up C :cited-by [child])]
      (is (map? children))
      (is (map? parents))
      (is (> (count (children id)) 0))
      (is (> (count (parents child)) 0))
      (is (contains? (parents child) id)))
    (is (= '("WOS:A1997WM73200001") (known-ids C #{"WOS:A1997WM73200001" "A" "B"})))))

(deftest to-cache-test
  (let [{:keys [cites cited-by details]} (init nil)]
    (doall (doseq [ca [cites cited-by details]]
             (#'malba.cache/to-cache ca {1 1 2 2 3 3 4 4 5 5})
             (is (= 5 (.size ca)))))))