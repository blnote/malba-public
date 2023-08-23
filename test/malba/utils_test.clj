(ns malba.utils-test
  (:require
   [clojure.test :refer [deftest is]]
   [malba.utils :refer [copy-to-clipboard filter-edges]]))

(deftest copy-to-clipboard-test
  (is (nil? (copy-to-clipboard "test"))))
