(ns malba.gui-test
  (:require
   [clojure.test :refer [deftest is use-fixtures]]
   [malba.gui :refer [init]]
   [malba.gephi :as gephi]))


(defn- with-ui [t]
  (init {:preview (gephi/init) :event-dispatch identity :version "test" :exit_on_close false}) 
  (Thread/sleep 100) 
  (t) 
  )

(use-fixtures :once with-ui)

(deftest db-info-test
  (let [
        {:keys [set-db-info get-db-info]} @@#'malba.gui/UI
        test-data {:user "user" :password "12345" :url "jdbc://my-database"}]
    (set-db-info test-data)
    (is (= test-data (get-db-info)))))

(deftest set-params-test
  (let [{:keys [set-params get-params]} @@#'malba.gui/UI
        test-data {:name "MALBA" :dc-in 0.8 :dc-out 9 :bc 0.7 
                   :max-subgraph-size 5000
                   :max-parents-of-shared-refs 1000}]
    (set-params test-data)
    (is (= test-data (get-params)))))
