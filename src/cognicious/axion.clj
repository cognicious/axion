(ns cognicious.axion
  (:gen-class)
  (:require
   [aleph.http :as http]
   [bidi.ring :as bidi]
   [clojure.data.json :as json]
   [clojure.java.io :as io]
   [clojure.tools.logging :as log]
   [cognicious.screenshot :as screen]
   [cognicious.system :as sys]))

(def meta-project "META-INF/leiningen/cognicious/axion/project.clj")
(def paused-atm (atom false))
(def config-atm (atom {}))

(defn project-clj 
  "Returns project.clj into the JAR, otherwise, return local file"
  []
  (if-let [project (io/resource meta-project)]
    project
    (do
      (log/warn (pr-str {:not-found meta-project :trying-with "project.clj"}))
      "project.clj")))

(defn name&version
  "Returns name and version reading project.clj"
  []
  (let [[_ name version] (-> (project-clj) slurp read-string vec)]
    [name version]))

(defmacro defhandler [name [req-sym tx-sym] & body]
  `(defn ~name [~req-sym]
     (let [params# (:query-params ~req-sym)
           tx# (get params# "tx" (str (java.util.UUID/randomUUID)))
           _# (log/info (pr-str {:status :start :uri (:uri ~req-sym) :tx tx#}))
           result# (let [~tx-sym tx#] ~@body)
           _# (log/debug (pr-str result#))]
       (log/info (pr-str {:status :end :result-code (:status result#) :uri (:uri ~req-sym) :tx tx#}))
       result#)))

(defhandler info [request tx]
  (cond-> {:status 204}
    (not @paused-atm) (assoc :status 200
                         :headers {"content-type" "application/json"}
                         :body (sys/info @config-atm))))

(defhandler info-raw [request tx]
  (cond-> {:status 204}
    (not @paused-atm) (assoc :status 200
                         :headers {"content-type" "application/json"}
                         :body (sys/info-raw))))

(defhandler index [request tx]
  (cond-> {:status 204}
    (not @paused-atm) (assoc :status 200 
                         :headers {"content-type" "application/json"} 
                         :body (json/write-str (name&version)))))

(defhandler pause [request tx]
  (reset! paused-atm true)
  {:status 204})

(defhandler restart [request tx]
  (reset! paused-atm false)
  {:status 200
   :headers {"content-type" "application/json"}
   :body (json/write-str {:status "restarted"})})

(defhandler screenshot [request tx]
  (cond-> {:status 204}
    (not @paused-atm) (assoc :status 200
                         :headers {"content-type" "image/png"}
                         :body (screen/take))))

(def handler
  (bidi/make-handler ["/" {"" index
                           "info" info
                           "info-raw" info-raw
                           "pause" pause
                           "restart" restart
                           "screenshot" screenshot}]))

(defn -main
  [& args]
  (let [config (or (System/getProperty "axion.config") (io/resource "config.axn"))
        _ (log/debug (pr-str {:reading-config config}))
        {:keys [http-port
                tcp-push-host
                tcp-push-port
                tcp-push-period
                storage-default
                network-default]
         :as config} (read-string (slurp config))
        _ (reset! config-atm config)
        _ (log/debug (pr-str {:config config}))
        server (http/start-server handler {:port http-port})]
    (log/info (pr-str {:starting (name&version)}))
    (while [true]
      (if-not @paused-atm
        (-> (sys/info config)
            (sys/send-data tcp-push-host tcp-push-port)))
      (Thread/sleep tcp-push-period))
    (.wait-for-close server)))
