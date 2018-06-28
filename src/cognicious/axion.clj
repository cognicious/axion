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
(def paused (atom false))

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
    (not @paused) (assoc :status 200
                        :headers {"content-type" "application/json"}
                        :body (sys/info))))

(defhandler info-raw [request tx]
  (cond-> {:status 204}
    (not @paused) (assoc :status 200
                        :headers {"content-type" "application/json"}
                        :body (sys/info-raw))))

(defhandler index [request tx]
  (cond-> {:status 204}
    (not @paused) (assoc :status 200 
                        :headers {"content-type" "text/plain"} 
                        :body "hello!")))

(defhandler pause [request tx]
  (reset! paused true)
  {:status 204})

(defhandler restart [request tx]
  (reset! paused false)
  {:status 200
   :headers {"content-type" "application/json"}
   :body (json/write-str {:status "restarted"})})

(defhandler screenshot [request tx]
  (cond-> {:status 204}
    (not @paused) (assoc :status 200
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
  (let [server (http/start-server handler {:port 5555})]
    (log/info (pr-str {:starting (name&version)}))
    (.wait-for-close server)))
