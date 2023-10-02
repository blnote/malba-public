;; Copyright 2023 blnote.
;; This file is part of MALBA.

(ns malba.logger
  (:require [clojure.string :as string]
            [malba.gui :as gui]))

(def DEBUG (atom false))

(defn debug "tab when DEBUG is true" [msg]
  (when @DEBUG (tap> msg)))

(defn text "log to text area" [msg]
  (gui/invoke :log-text msg)
  (when @DEBUG (tap> {:text msg})))

(defn status "log to status bar" [msg]
  (gui/invoke :log-status msg)
  (when @DEBUG (tap> {:status msg})))

(defn error "log error to text area and status bar" [msg]
  (gui/invoke :log-error msg)
  (gui/invoke :task-stopped "Error!")
  (when @DEBUG (tap> {:error msg})))

(defn cache "log to cache label" [msg]
  (gui/invoke :log-cache msg)
  (when @DEBUG (tap> {:cache msg})))

(defn event-to-status
  "sends status message for given event at start or stop, depending on 
   mode (:start :stop). nothing is sent if event not found in dict."
  [event mode]
  (let [msg (condp = event
              "read-db-config" "Reading db configuration"
              "init-algo" "Initializing"
              "load-seed" "Loading seed"
              "load-network" "Loading network"
              "clear-cache" "Clearing cache"
              "db-connect" "Connecting"
              "load-session" "Loading session"
              "save-session" "Saving session"
              "export" "Exporting"
              "algo-reset" "Resetting algorithm"
              "algo-stop" "Stopping algorithm"
              "algo-step" "Algorithm step"
              "algo-run" "Running algorithm"
              "algo-search" "Searching parameters"
              "layout" "Generating layout"
              "copy-to-clipboard" "Copying to clipboard"
              nil)]
    (when msg (condp = mode
                :start (gui/invoke :task-started (string/join [msg "..."]))
                :stop (gui/invoke :task-stopped (string/join [msg " done."]))
                msg))))
