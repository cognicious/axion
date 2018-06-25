(defproject cognicious/axion "0.1.0-SNAPSHOT"
  :description "Lightweight Cross-platform Monitoring Agent"
  :url "https://github.com/cognicious/axion"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/data.json "0.2.6"]
                 ;; server info
                 [com.github.oshi/oshi-json "3.6.0"]
                 ;; webserver
                 [aleph "0.4.6"]
                 ;; logging
                 [org.apache.logging.log4j/log4j-core "2.11.0"]
                 [org.apache.logging.log4j/log4j-slf4j-impl "2.11.0"]]
  :main cognicious.axion)
