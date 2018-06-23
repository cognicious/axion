(ns cognicious.axion
  (:gen-class)
  (:import
   (oshi.json SystemInfo)))

(defn -main
  [& args]
  (println "Hello, World!")
  (let [si (SystemInfo.)]
    (println (-> si .toJSON))
    (println)
    (println)
    (println)
    (println (-> si .getOperatingSystem .toJSON))))
