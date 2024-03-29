(defproject cognicious/axion "0.3.1-SNAPSHOT"
  :description "Lightweight Cross-platform Monitoring Agent"
  :url "https://github.com/cognicious/axion"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/java.data "0.1.1"]
                 ;; platform info
                 [com.github.oshi/oshi-core "5.8.3"]
                 ;; communication
                 [aleph/aleph "0.4.6"]
                 [bidi/bidi "2.1.3"]
                 [clj-http/clj-http "3.10.0"]
                 ;; logging
                 [org.apache.logging.log4j/log4j-core "2.11.0"]
                 [org.apache.logging.log4j/log4j-slf4j-impl "2.11.0"]
		 ;;
		 [javax.xml.bind/jaxb-api "2.2.11"]
                 [com.sun.xml.bind/jaxb-core "2.2.11"]
                 [com.sun.xml.bind/jaxb-impl "2.2.11"]
                 [javax.activation/activation "1.1.1"]
		]
  :plugins [[lein-cloverage "1.0.11"]]
  :main cognicious.axion.app
  :aot :all)
