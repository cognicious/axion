(ns cognicious.axion.client
  (:require [aleph.http :as http]
            [byte-streams :as bs]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [cognicious.axion.config :as conf]
            [cognicious.axion.screenshot :as screen]
            [cognicious.axion.system :as sys]))

(def connection-pool (http/connection-pool
                      {:connections-per-host 4
                       :connection-options   {:keep-alive? true}}))

(defmulti state-command (fn [key value id streamer-push-url]
                          (let [[_ command remote-id] (re-matches #"\[:([a-zA-Z0-9\-]+) \"([a-zA-Z0-9:\-]+)\"\]" (name key))]
                            (when (and command (= id remote-id))
                              (keyword command)))))

(defmethod state-command :screen [key value id streamer-push-url]
  (-> value
      (assoc :screenshot (screen/take64))
      (json/write-str :key-fn #(str (.-sym %)))
      (sys/send-data streamer-push-url)))

(defmethod state-command :config [key value id _]
  (let [path (.getCanonicalPath (clojure.java.io/file "./config.edn"))
        current (-> path slurp read-string)
        _ (log/debug (pr-str {:current current}))
        value (dissoc value :caudal/created :caudal/touched :remote-addr)
        _ (log/debug (pr-str {:online value}))]
    (when-not (= current value)
      (log/warn (pr-str {:config-replace value}))
      (spit conf/path value))))

(defmethod state-command :default [key _ _ _]
  (log/debug "Nothing to do ... " (pr-str key)))

(defn state-reducer [id streamer-push-url]
  (fn [a [key value]]
    (log/debug :state-reducer (pr-str [key value]))
    (state-command key value id streamer-push-url)))

(defn retrieve-state [http-poll-url connection-pool]
  (-> @(http/request {:url http-poll-url
                      :request-method "get"
                      :pool connection-pool})
      :body
      bs/to-string
      (json/read-str :key-fn keyword)))

(defn poll-state [streamer-push-url streamer-poll-url id]
  (try
    (reduce
     (state-reducer id streamer-push-url)
     nil
     (retrieve-state streamer-poll-url connection-pool))
    (catch Exception e
      (log/error e))))
