(ns malba.integration-test
  (:require [clojure.string :as string]
            [clojure.test :refer [deftest is]]
            [malba.core :refer [-main event-dispatch worker]]))
(import (java.io File))
(deftest integration-local-test []
  (-main)
  ;initialization
  (event-dispatch "load-seed" (File. "test/data/seedWOS.txt"))
  (event-dispatch "load-network" (File. "test/data/networkWOS.txt"))
  (await @worker)
  ;algo
  (event-dispatch "algo-search" {})
  (Thread/sleep 10000)
  (event-dispatch "algo-stop" {})
  (event-dispatch "algo-run" {})
  (await @worker)
  ;layout
  (event-dispatch "view-surrounding" {})
  (event-dispatch "layout" "yifan")
  (event-dispatch "layout" "frucht")
  (event-dispatch "layout" "overlap")
  (await @worker)
  ;hovering and focusing
  (event-dispatch "hovered" {:event [162.7938 5.4403839111328125]
                             :time (System/currentTimeMillis)})
  (event-dispatch "view-neighbors" [162.7938 5.4403839111328125])
  (event-dispatch "view-reset" {})

  (event-dispatch "algo-reset")
  (await @worker)
  ;exporting
  (let [dir-name "test/data/exports"
        exports-dir (File. dir-name)]
    (when (.exists exports-dir) ;delete contents if exists
      (->> (.listFiles exports-dir)
           (map #(.delete %))
           (doall)))
    (event-dispatch "export" [(File. (string/join [dir-name "/malba.pdf"])) "pdf"]) 
    (event-dispatch "export" [(File. (string/join [dir-name "/malba.csv"])) "csv"]) 
    (event-dispatch "export" [(File. (string/join [dir-name "/malba.gexf"])) "gexf"]) 
    (event-dispatch "save-session" (File. (string/join [dir-name "/test.session"])))
    (event-dispatch "load-session" (File. (string/join [dir-name "/test.session"])))
    (await @worker)
    (is (= 4 (count (.listFiles exports-dir))) "count export files")))