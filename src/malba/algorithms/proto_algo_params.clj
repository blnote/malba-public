;; Copyright 2023 blnote.
;; This file is part of MALBA.

(ns malba.algorithms.proto-algo-params)

(defprotocol AlgoParams
  "protocol for algorithm parameters" 
  (init [this] "initialize with default parameters")
  (get-vars [this] "get variable parameters as (sub) map")
  (to-string [this] "string representation of parameters")
  (update-vars [this params] "update parameters")
  (loosen [this param] "tighten parameter param by a predefined step, nil if not possible")
  (tighten [this param] "loosen parameter param by a predefined step, nil if not possible")
  )