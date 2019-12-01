(ns cognicious.axion.client
  (:require ;[aleph.http :as http]
            ;[byte-streams :as bs]
            [clj-http.client :as http]
            [clojure.data :refer [diff]]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [cognicious.axion.config :as conf]
            [cognicious.axion.screenshot :as screen])
  (:import (java.util.concurrent TimeoutException TimeUnit)))

(http/with-connection-pool {:timeout 90 :threads 4 :default-per-route 4}

  (defn body->edn [response]
    (-> response :body (json/read-str :key-fn keyword)))

  (defn retrieve* [http-poll-url cmd id timeout]
    (log/debug (pr-str [http-poll-url cmd id timeout]))
    (let [value (-> http-poll-url
                    (str "/" cmd "/" id)
                    (http/get {:connection-timeout timeout :insecure? true})
                    :body
                    (json/read-str :key-fn keyword))]
      (if (not (empty? value)) {cmd value})))

  (defn send-data [local url http-poll-url id timeout]
    (let [online (retrieve* http-poll-url "check" id timeout)]
      (if (not (empty? online))
        (let [[things-only-in-online things-only-in-local things-in-both] (diff online local)
              [timeout data] (cond (nil? things-in-both) [(* timeout 35) things-only-in-local] ;; Big Load
                                   things-only-in-local  [timeout things-only-in-local]        ;; Moderate Load, only changes
                                   :otherwise [nil nil])]
          (if (and timeout data)
            (http/post url {:connection-timeout timeout
                            :insecure? true
                            :body data
                            :headers {"content-type" "application/json"}})
            (log/warn (pr-str {:no-changes "online data is same as local data!"}))))
        (log/warn (pr-str {:no-check (str "send /check " id " to telegram!")})))))

  (defn send-config [url timeout]
    (let [path (.getCanonicalPath (clojure.java.io/file "./config.edn"))
          data (-> path slurp read-string)
          future (http/post url {:async true
                                 :insecure? true
                                 :body (pr-str data)
                                 :headers {"content-type" "application/edn"}
                                 :oncancel #(log/error "request was cancelled " (* timeout 35))}
                            #(log/debug :got %)
                            #(log/error :err %))]
      (try
        (.get future (* timeout 35) TimeUnit/MILLISECONDS)
        (catch TimeoutException e
          (.cancel future true)))))

  (defmulti state-command (fn [key value id streamer-push-url timeout]
                            (keyword key)))

  (defmethod state-command :screen [key value id streamer-push-url timeout]
    (-> value
        (assoc :screenshot (screen/take64))
        (json/write-str :key-fn #(str (.-sym %)))
        (send-data streamer-push-url timeout)))

  (defmethod state-command :config [key value id _ _]
    (let [path (.getCanonicalPath (clojure.java.io/file "./config.edn"))
          current (-> path slurp read-string)
          _ (log/debug (pr-str {:current current}))
          value (dissoc value :caudal/created :caudal/touched :remote-addr)
          _ (log/debug (pr-str {:online value}))]
      (when-not (= current value)
        (log/warn (pr-str {:config-replace value}))
        (spit conf/path value))))

  (defmethod state-command :default [key _ _ _ _]
    (log/debug "Nothing to do ... " (pr-str key)))

  (defn state-reducer [id streamer-push-url timeout]
    (fn [a [key value]]
      (log/debug :state-reducer (pr-str [key value]))
      (state-command key value id streamer-push-url timeout)))

  (defn retrieve-state [http-poll-url id timeout]
    (-> {}
        (merge (retrieve* http-poll-url "config" id timeout))
        (merge (retrieve* http-poll-url "screen" id timeout))))

  (defn poll-state [streamer-push-url streamer-poll-url id timeout]
    (reduce
     (state-reducer id streamer-push-url timeout)
     nil
     (retrieve-state streamer-poll-url id timeout))))
