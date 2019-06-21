(ns cognicious.axion.app
  (:gen-class)
  (:require [aleph.http :as http]
            [bidi.ring :as bidi]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as spec]
            [clojure.string :as s]
            [clojure.tools.logging :as log]
            [cognicious.axion.client :as client]
            [cognicious.axion.server :as server]
            [cognicious.axion.system :as sys]))

(def meta-project "META-INF/leiningen/cognicious/axion/project.clj")
(def default-config {:axn/uuid (java.util.UUID/randomUUID)
                     :axn/server-host "localhost"
                     :axn/server-port 8081
                     :axn/push-period 10000
                     :axn/streamer "http://axion.cognicio.us"})

(defn project-clj 
  "Returns project.clj into the JAR, otherwise, return local file"
  [meta]
  (if-let [project (io/resource meta)]
    project
    (do
      (log/warn (pr-str {:not-found meta :trying-with "project.clj"}))
      "project.clj")))

(defn name-version
  "Returns name and version reading project.clj"
  []
  (let [[_ name version] (-> (project-clj meta-project) slurp read-string vec)]
    {:name name :version version}))

(defn banner
  "Fancy app banner"
  []
  (s/split-lines (slurp (io/resource "banner.txt"))))

(defn shutdown-hook [app]
  (.addShutdownHook
   (Runtime/getRuntime)
   (Thread. #(log/info (pr-str {:stop app})))))

(defn create-config! 
  "Create default configuration file"
  [path]
  (try
    (log/info (pr-str {:message "Creating default configuration file" :path path}))
    (spit path default-config)
    default-config
    (catch Exception e
      (log/fatal (pr-str {:message (.getMessage e)})))))

(defn get-config 
  "Retrieves configuration file"
  [path]
  (try 
    (read-string (slurp path))
    (catch Exception e
      (log/warn (pr-str {:message (.getMessage e)}))
      (create-config! path))))

(defn valid-url? [string]
  #(re-matches #"^(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]" string))

(spec/def :axn/uuid uuid?)
(spec/def :axn/server-host string?)
(spec/def :axn/server-port (spec/and number? #(<= 0 % 65535)))
(spec/def :axn/push-period number?)
(spec/def :axn/streamer (spec/and string? valid-url?))
(spec/def :axn/merge-data map?)
(spec/def :axn/storage-default string?)
(spec/def :axn/network-default string?)
(spec/def :axn/config (spec/keys :req [:axn/uuid
                                       :axn/server-port
                                       :axn/push-period
                                       :axn/streamer]
                                 :opt [:axn/server-host 
                                       :axn/merge-data
                                       :axn/storage-default
                                       :axn/network-default]))
(spec/fdef get-config
           :args (spec/cat :path string?) 
           :ret :axn/config)

(defn -main
  [& args]
  (let [app (name-version)
        _ (shutdown-hook app)
        path (.getCanonicalPath (clojure.java.io/file "./config.edn"))]
    (doall (map #(log/info %) (banner)))
    (log/info (pr-str {:start app}))
    (log/info (pr-str {:reading-config-file path}))
    (let [{:axn/keys [server-host
                      server-port
                      push-period
                      streamer
                      storage-default
                      network-default
                      merge-data]
           :or {server-host "localhost"}
           :as config} (get-config path)]
      (if (spec/valid? :axn/config config)
        (let [server (server/start-server config app)
              streamer-push-url (str streamer ":9999/event")
              streamer-poll-url (str streamer ":10000/state/http-streamers")]
          (while [true]
            (if-not @server/paused-atm
              (let [info (sys/info config)
                    _ (log/debug info)
                    net-mac (:net-mac (json/read-str info :key-fn keyword))
                    _ (log/debug {:net-mac net-mac})]
                (sys/send-data info streamer-push-url)
                (if net-mac
                  (client/poll-state streamer-push-url streamer-poll-url net-mac))))
            (Thread/sleep push-period)))
        (log/fatal (spec/explain-str :axn/config config))))))
