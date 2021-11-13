(ns cognicious.axion.app
  (:gen-class)
  (:require [bidi.ring :as bidi]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as spec]
            [clojure.string :as s]
            [clojure.tools.logging :as log]
            [cognicious.axion.client :as client]
            [cognicious.axion.config :as conf]
            [cognicious.axion.server :as server]
            [cognicious.axion.system :as sys]))

(def error (atom ""))
(def meta-project "META-INF/leiningen/cognicious/axion/project.clj")

(defn project-clj 
  "Returns project.clj into the JAR, otherwise, return local file"
  [meta]
  (if-let [project (io/resource meta)]
    project
    (do
      (log/warn (pr-str {:not-found meta :trying-with "project.clj"}))
      "project.clj")))

(defn name-version
  "Returns name and version reading project.clj"
  []
  (let [[_ name version] (-> (project-clj meta-project) slurp read-string vec)]
    {:name name :version version}))

(defn banner
  "Fancy app banner"
  []
  (s/split-lines (slurp (io/resource "banner.txt"))))

(defn shutdown-hook [app]
  (.addShutdownHook
   (Runtime/getRuntime)
   (Thread. #(log/info (pr-str {:stop app})))))

(defn draw []
  (try
      (let [{:axn/keys [id]} @conf/config]
        (javax.swing.UIManager/setLookAndFeel (javax.swing.UIManager/getCrossPlatformLookAndFeelClassName))
        (doto (javax.swing.JFrame. ":axion/id")
          (.setContentPane (doto (proxy [javax.swing.JPanel] []
                                   (paint [^java.awt.Graphics g]
                                     (let [curr-font (-> g (.getFont))
                                           big-font (-> curr-font (.deriveFont (* (.getSize curr-font) 3.0)))
                                           small-font (-> curr-font (.deriveFont (* (.getSize curr-font) 0.7)))]
                                       (.setRenderingHint g java.awt.RenderingHints/KEY_TEXT_ANTIALIASING
                                                          java.awt.RenderingHints/VALUE_TEXT_ANTIALIAS_LCD_HRGB)
                                       (.setFont g big-font)
                                       (.drawString g id 20 50)
                                       (.setFont g small-font)
                                       (.drawString g @error 20 100))))
                             (.setPreferredSize (java.awt.Dimension. 300 150))))
          (.setDefaultCloseOperation javax.swing.JFrame/DO_NOTHING_ON_CLOSE) 
          (.setState javax.swing.JFrame/ICONIFIED)
          (.pack)
          (.setVisible true)))
      (catch Throwable e
        (log/warn (pr-str {:gui-error (.getMessage e)
                           :no-gui "System without UI detected, check config.edn for :axion/id"})))))

(defn -main
  [& args]
  (let [app (name-version)
        _ (shutdown-hook app)]
    (doall (map #(log/info %) (banner)))
    (log/info (pr-str {:start app}))
    (let [{:axn/keys [id
                      server-host
                      server-port
                      push-period
                      push-timeout
                      push-port
                      poll-port
                      streamer
                      storage-default
                      network-default
                      merge-data]
           :or {server-host "localhost" push-port 9999 poll-port 10000}
           :as config} (conf/get-config!)
          _ (log/debug config)]
      (let [server (server/start-server config app)
            streamer-push-url (str streamer ":" push-port "/event")
            streamer-poll-url (str streamer ":" poll-port "/state/http-streamers")
            window (draw)]
          (while [true]
            (try
              (if-not @server/paused-atm
                (let [{:axn/keys [id
                                  server-host
                                  server-port
                                  push-period
                                  push-timeout
                                  streamer
                                  storage-default
                                  network-default
                                  merge-data]
                       :or {server-host "localhost"}
                       :as config} (conf/get-config!)
                      info (sys/info config)
                      _ (log/debug info)]
                  (if id
                    (client/poll-state streamer-push-url streamer-poll-url id push-timeout))
                  (client/send-data info streamer-push-url streamer-poll-url id push-timeout)
                  (client/send-config streamer-push-url push-timeout)
                  (when-not (= @error "Online")                    
                    (reset! error "Online"))))
              (catch Throwable t
                (.printStackTrace t)
                (reset! error (.getMessage t))
                (log/warn {:previous-errors (.getMessage t)}))
              (finally
                (if window
                  (javax.swing.SwingUtilities/updateComponentTreeUI window))))
            (Thread/sleep push-period))))))
