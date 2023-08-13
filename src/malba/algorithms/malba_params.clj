;; Copyright 2023 blnote.
;; This file is part of MALBA.

(ns malba.algorithms.malba-params
  (:require [malba.algorithms.proto-algo-params :as proto]
            [malba.logger :as l] 
            [malba.file-io :as f]))

(def config-file "./malba-algo.edn")

(def default-params {:name  "MALBA"

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

(defn- read-config []
  (if-let [conf (f/load-config config-file)] 
    (do 
      (l/text (format "Config loaded from %s." config-file))
      conf)
    (do 
      (l/text (format "Config not readable from %s, using default." config-file))
      {})))
  
(defrecord Params []
  proto/AlgoParams 
  (init [this] (merge this default-params (read-config)))
  (to-string [this] (let [{:keys [dc-out bc dc-in]} this]
                      (format "DCout: %d  DCin: %s BC: %s" dc-out dc-in bc)))
  (get-vars [this]
    (select-keys this [:name :dc-out :bc :dc-in :max-subgraph-size :max-parents-of-shared-refs]))

  (update-vars [this {:keys [bc dc-in dc-out max-subgraph-size max-parents-of-shared-refs] :as ps}]
    (when (and dc-out (< dc-out 0))
      (throw (IllegalArgumentException. "Parameter DCout must be positive!")))
    (when (and bc (< bc 0) (> bc 1))
      (throw (IllegalArgumentException. "Parameter BC must be between 0 and 1!")))
    (when (and dc-in (< dc-in 0) (> dc-in 1))
      (throw (IllegalArgumentException. "Parameter DCin must be between 0 and 1!")))
    (when (and (contains? ps :max-subgraph-size) (or (nil? max-subgraph-size) (<= max-subgraph-size 0)))
      (throw (IllegalArgumentException. "Parameter max. graph size must be number greater than 0!")))
    (when (and (contains? ps :max-parents-of-shared-refs) (or (nil? max-parents-of-shared-refs) (<= max-parents-of-shared-refs 0)))
      (throw (IllegalArgumentException. "Parameter max. parents... must be number greater than 0!")))
    (merge this ps))

  (loosen [{:keys [min-p step] :as this} p] 
            (let  [new-p 
                   (if (= p :dc-out) ;dc-out is integer, rest are double parameters)
                     (- (get this p)  (step p)) 
                     (double (- (bigdec (str (get this p))) (bigdec (str (step p))))))]
              (when (>= new-p (min-p p)) (assoc this p new-p))))
  
  (tighten [{:keys [max-p step] :as this} p]
          (let  [new-p
                 (if (= p :dc-out) ;dc-out is integer, rest are double parameters)
                   (+ (get this p)  (step p))
                   (double (+ (bigdec (str (get this p))) (bigdec (str (step p))))))]
            (when (<= new-p (max-p p)) (assoc this p new-p)))))
