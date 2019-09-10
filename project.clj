(defproject cognicious/axion "0.2.2-SNAPSHOT"
  :description "Lightweight Cross-platform Monitoring Agent"
  :url "https://github.com/cognicious/axion"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/data.json "0.2.6"]
                 ;; platform info
                 [com.github.oshi/oshi-json "3.9.1"]
                 ;; communication
                 [aleph "0.4.6"]
                 [bidi "2.1.3"]
                 ;; logging
                 [org.apache.logging.log4j/log4j-core "2.11.0"]
                 [org.apache.logging.log4j/log4j-slf4j-impl "2.11.0"]]
  :plugins [[lein-cloverage "1.0.11"]]
  :main cognicious.axion.app
  :aot :all)
