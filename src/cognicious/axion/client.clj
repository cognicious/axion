(ns cognicious.axion.client
  (:require [aleph.http :as http]
            [byte-streams :as bs]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [cognicious.axion.screenshot :as screen]
            [cognicious.axion.system :as sys]))

(def connection-pool (http/connection-pool
                      {:connections-per-host 4
                       :connection-options   {:keep-alive? true}}))

(defmulti state-command (fn [key value local-mac streamer-push-url]
                          (let [[_ command remote-mac] (re-matches #"\[:([a-zA-Z0-9\-]+) \"([a-z0-9:\-]+)\"\]" key)]
                            (when (and command (= local-mac remote-mac))
                              (keyword command)))))

(defmethod state-command :screen [key value local-mac streamer-push-url]
  (-> value
      (assoc :screenshot (screen/take64))
      json/write-str
      (sys/send-data streamer-push-url)))

(defmethod state-command :default [_ _ _ _]
  (log/debug "Nothing to do ..."))

(defn state-reducer [local-mac streamer-push-url]
  (fn [a [key value]]
    (state-command key value local-mac streamer-push-url)))

(defn retrieve-state [http-poll-url connection-pool]
  (-> @(http/request {:url http-poll-url
                      :request-method "get"
                      :pool connection-pool})
      :body
      bs/to-string
      json/read-str))

(defn poll-state [streamer-push-url streamer-poll-url mac]
  (try
    (reduce
     (state-reducer mac streamer-push-url)
     nil
     (retrieve-state streamer-poll-url connection-pool))
    (catch Exception e
      (.printStackTrace e)
      (log/error e))))
