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

(defn get-config [path]
  (try 
    (read-string (slurp path))
    (catch Exception e
      (log/fatal (pr-str {:message (.getMessage e)})))))

(defn valid-url? [string]
  #(re-matches #"^(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]" string))

(spec/def :axn/server-host string?)
(spec/def :axn/server-port (spec/and number? #(<= 0 % 65535)))
(spec/def :axn/tcp-push-host string?)
(spec/def :axn/tcp-push-port (spec/and number? #(<= 0 % 65535)))
(spec/def :axn/tcp-push-period number?)
(spec/def :axn/http-poll-url (spec/and string? valid-url?))
(spec/def :axn/merge-data map?)
(spec/def :axn/storage-default string?)
(spec/def :axn/network-default string?)
(spec/def :axn/config (spec/keys :req [:axn/server-port
                                       :axn/tcp-push-host
                                       :axn/tcp-push-port
                                       :axn/tcp-push-period
                                       :axn/http-poll-url] 
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
    (let [{:axn/keys [http-port
                      tcp-push-host
                      tcp-push-port
                      tcp-push-period
                      http-pull-url
                      storage-default
                      network-default
                      merge-data]
           :as config} (get-config path)]
      (if (spec/valid? :axn/config config)
        (let [server (server/start-server config)] 
          (while [true]
            (if-not @server/paused-atm
              (let [info (sys/info config)
                    net-mac (:net-mac (json/read-str info :key-fn keyword))
                    _ (log/info {:net-mac net-mac})]
                (sys/send-data info tcp-push-host tcp-push-port)
                (if (and http-pull-url net-mac)
                  (client/poll-state http-pull-url tcp-push-host tcp-push-port net-mac))))
            (Thread/sleep tcp-push-period)))
        (log/fatal (spec/explain-str :axn/config config))))))
