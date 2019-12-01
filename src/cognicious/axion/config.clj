(ns cognicious.axion.config
  (:gen-class)
  (:require [aleph.http :as http]
            [bidi.ring :as bidi]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as spec]
            [clojure.string :as s]
            [clojure.tools.logging :as log]))

(defn rand-str [len]
  (apply str (take len (repeatedly #(char (+ (rand 26) 65))))))

(defn gen-axion-streamer []
  (if-let [streamer (System/getenv "AXION_STREAMER_DEFAULT")]
    streamer
    "https://axion.cognicio.us"))

(defn gen-axion-local-server []
  (if-let [server (System/getenv "AXION_LOCALSERVER_DEFAULT")]
    server
    "localhost"))

(def config (atom nil))

(def default-config {:axn/id (rand-str 6)
                     :axn/server-host (gen-axion-local-server)
                     :axn/server-port 8081
                     :axn/push-port 9999
                     :axn/poll-port 10000
                     :axn/push-period 10000
                     :axn/push-timeout 5000
                     :axn/streamer (gen-axion-streamer)})

(def path (.getCanonicalPath (clojure.java.io/file "./config.edn")))

(defn create-config!
  "Create default configuration file"
  []
  (try
    (log/info (pr-str {:message "Creating default configuration file" :path path}))
    (spit path default-config)
    (reset! config default-config)
    (catch Exception e
      (log/fatal (pr-str {:message (.getMessage e)})))))

(defn get-config!
  "Retrieves configuration file"
  []
  (try 
    (log/debug (pr-str {:reading path}))
    (reset! config (read-string (slurp path)))
    (catch Exception e
      (log/warn (pr-str {:message (.getMessage e)}))
      (create-config!))))

(defn valid-url? [string]
  #(re-matches #"^(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]" string))

(spec/def :axn/id string?)
(spec/def :axn/server-host string?)
(spec/def :axn/server-port (spec/and number? #(<= 0 % 65535)))
(spec/def :axn/push-period number?)
(spec/def :axn/push-port (spec/and number? #(<= 0 % 65535)))
(spec/def :axn/poll-port (spec/and number? #(<= 0 % 65535)))
(spec/def :axn/streamer (spec/and string? valid-url?))
(spec/def :axn/merge-data map?)
(spec/def :axn/storage-default string?)
(spec/def :axn/network-default string?)
(spec/def :axn/config (spec/keys :req [:axn/id
                                       :axn/server-port
                                       :axn/push-period
                                       :axn/push-timeout
                                       :axn/streamer]
                                 :opt [:axn/server-host 
                                       :axn/merge-data
                                       :axn/storage-default
                                       :axn/network-default
                                       :axn/push-port
                                       :axn/poll-port]))
(spec/fdef get-config
           :args (spec/cat :path string?) 
           :ret :axn/config)
