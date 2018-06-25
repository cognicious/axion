(ns cognicious.axion
  (:gen-class)
  (:require
   [clojure.data.json :as json]
   [clojure.java.io :as io]
   [clojure.pprint :as pp])
  (:import
   (java.util Properties)
   (oshi.json SystemInfo)
   (oshi.util EdidUtil)))

(def properties-filename "oshi.json.properties")

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
        (json/read-str :key-fn keyword))))

(defn -main
  [& args]
  (let [mk1 (System/currentTimeMillis)]
    (pp/pprint (system-info))
    (println (- (System/currentTimeMillis) mk1) "ms")))
