(ns cognicious.axion.server
  (:require [aleph.http :as http]
            [bidi.ring :as bidi]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [cognicious.axion.system :as sys]
            [cognicious.axion.screenshot :as screen]))

(def paused-atm (atom false))
(def config-atm (atom {}))

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

(defn index-factory [app uuid]
  (defhandler index [request tx]
    (cond-> {:status 204}
      (not @paused-atm) (assoc :status 200 
                               :headers {"content-type" "application/json"} 
                               :body (json/write-str {:app app 
                                                      :uuid (str uuid)})))))

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

(defn start-server [{:axn/keys [server-host server-port uuid]
                     :or {server-host "0.0.0.0" 
                          server-port 8081} 
                     :as config}
                    app ]
  (log/info (pr-str {:start-server [server-host server-port]}))
  (reset! config-atm config)
  (http/start-server 
   (bidi/make-handler ["/" {"" (index-factory app uuid)
                            "info" info
                            "info-raw" info-raw
                            "pause" pause
                            "restart" restart
                            "screenshot" screenshot}])
   {:socket-address (java.net.InetSocketAddress. server-host server-port)}))
