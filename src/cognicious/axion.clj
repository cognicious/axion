(ns cognicious.axion
  (:gen-class)
  (:require
   [aleph.http :as http]
   [clojure.data.json :as json]
   [clojure.java.io :as io]
   [clojure.tools.logging :as log]
   ;[clojure.pprint :as pp]
   )
  (:import
   (java.util Properties)
   (oshi.json SystemInfo)
   (oshi.util EdidUtil)))

(def meta-project "META-INF/leiningen/cognicious/axion/project.clj")
(def properties-filename "oshi.json.properties")

(defn project-clj []
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

(defn properties-file []
  (with-open [is (-> (io/resource properties-filename)
                     (io/input-stream))]
    (doto (Properties.)
      (.load is))))

(defn system-info []
  (let [si (SystemInfo.)]
    (-> si 
        (.toJSON (properties-file)) 
        .toString
        ;(json/read-str :key-fn keyword)
        )))

(defn handler [{:keys [remote-addr] :as req}]
  (let [_ (log/info (pr-str {:retrieving :system-info :remote-addr remote-addr}))
        info (system-info)
        _ (log/info (pr-str {:publishing :system-info :remote-addr remote-addr}))]
    {:status 200
     :headers {"content-type" "application/json"}
     :body info}))

(defn -main
  [& args]
  (let [server (http/start-server handler {:port 8080})]
    (log/info (pr-str {:starting (name&version)}))
    (.wait-for-close server)))
