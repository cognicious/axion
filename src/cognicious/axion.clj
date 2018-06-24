(ns cognicious.axion
  (:gen-class)
  (:import
   (oshi.json SystemInfo)))

(def *properties-filename* "oshi.json.properties")

(defn get-properties-file []
  (with-open [is (-> (clojure.java.io/resource *properties-filename*)
                     (clojure.java.io/input-stream))]
    (doto (java.util.Properties.)
      (.load is))))

(defn -main
  [& args]
  (let [si (SystemInfo.)]
    (println (-> si (.toJSON (get-properties-file))))))
