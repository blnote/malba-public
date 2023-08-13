;; Copyright 2023 blnote.
;; This file is part of MALBA.

(ns malba.algorithms.proto-algo)

(defprotocol Algo
  "protocol for graph algorithm"
  (init [this cache seed] 
    "init algorithm with cache, a set of seed ids and parameters.
         default parameters are used for values not provided.") 
  (step [this] "run one cycle of algoritm ")
  (run [this] "run algorithm")
  (search [this] "search parameters leading largest subgraph < max-subgraph-size.")
  
  (generate-surrounding [this] "generate surrounding nodes for given algorithm state") 
  (subgraph [this] "current subgraph")

  (set-params [this params] "set parameters obtained from UI")
  (get-params [this] "parameters to send to UI. when not yet initialized, returns default parameters.")
  (get-params-as-string [this] "parameters as string for logging")

  (terminated? [this] "true if algorithm has terminated")
  (interrupted? [this] "true if algorithm has been interrupted")
  (error [this] "returns nil if there was no error, otherwise the error")
  (steps [this] "returns number of steps done")
  
  (to-stream! [this out] "write to output stream")
  (from-stream [this in cache] "read from intput stream")
  )