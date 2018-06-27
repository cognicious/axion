(ns cognicious.screenshot
  (:refer-clojure :exclude [take])
  (:require
   [clojure.java.io :as io])
  (:import 
   (java.awt GraphicsDevice GraphicsEnvironment Rectangle Robot)
   (java.awt.image BufferedImage)
   (javax.imageio ImageIO)))

(defn make-rect []
  (reduce (fn [r gd]
            (-> r
                (.union (-> gd .getDefaultConfiguration .getBounds))))
          (Rectangle. 0 0 0 0)
          (-> (GraphicsEnvironment/getLocalGraphicsEnvironment)
              .getScreenDevices)))

(defn take []
  (let [rect (make-rect)]
    (with-open [os (io/output-stream "./screenshot.png")]
      (-> (Robot.)
          (.createScreenCapture rect)
          (ImageIO/write "png" os)))
    (io/file "./screenshot.png")))
