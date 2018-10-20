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

(defn state-reducer [mac tcp-push-host tcp-push-port]
  (fn [a [k v]]
    (let [[_ f-mac] (re-matches #"\[:screen \"([a-z0-9:\-]+)\"\]" k)]
      (if (= mac f-mac)
        (-> v
            (assoc :screenshot (screen/take64))
            json/write-str
            (sys/send-data tcp-push-host tcp-push-port))))))

(defn retrieve-state [http-poll-url connection-pool]
  (-> @(http/request {:url http-poll-url
                      :request-method "get"
                      :pool connection-pool})
      :body
      bs/to-string
      json/read-str))

(defn poll-state [http-poll-url tcp-push-host tcp-push-port mac]
  (try
    (reduce
     (state-reducer mac tcp-push-host tcp-push-port)
     nil
     (retrieve-state http-poll-url connection-pool))
    (catch Exception e
      (.printStackTrace e)
      (log/error e))))
