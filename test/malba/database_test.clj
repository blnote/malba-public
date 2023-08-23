(ns malba.database-test
  (:require
   [clojure.test :refer [deftest is use-fixtures]]
   [malba.database]))

(deftest add-detail-labels-test []
  (is (=
         {0 {:pubyear "1000"
             :authors "A,B,C"
             :label "A(1000)"}
          1 {:pubyear "2000"
             :authors "A"
             :label "A(2000)"}
          2 {:pubyear "3000"}}
         (#'malba.database/add-detail-labels {0 {:pubyear "1000"
                                :authors "A,B,C"} 1 {:pubyear "2000" :authors "A"} 2 {:pubyear "3000"}}))))
