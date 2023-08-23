(ns malba.file-io-test
  (:require 
   [clojure.test :refer [deftest is]]
   [clojure.string :as string]
   [malba.file-io :refer [load-seed load-network load-config]]))

(deftest load-seed-test
  (let [seed (load-seed "test/data/seedWOS.txt")]
    (is (= (count seed) 29))
    (is (string/starts-with? (first seed) "WOS:"))
    (is (set? seed))))

(deftest load-network-test
  (let [{:keys [cites cited-by]} (load-network "test/data/networkWOS.txt")]
    (is (> (count cites) 50000))
    (is (> (count cited-by) 50000))))

(deftest load-config-test
  (let [res (load-config "malba-algo.edn")]
    (is (not-empty res))))