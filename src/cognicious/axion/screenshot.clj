(ns cognicious.axion.screenshot
  (:refer-clojure :exclude [take])
  (:require [clojure.java.io :as io])
  (:import (java.awt GraphicsDevice GraphicsEnvironment Rectangle Robot)
           (java.awt.image BufferedImage)
           (java.io ByteArrayOutputStream)
           (java.util Base64)
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

(defn take64 []
  (let [rect (make-rect)
        ]
    (with-open [os (ByteArrayOutputStream.)]
      (-> (Robot.)
          (.createScreenCapture rect)
          (ImageIO/write "png" os))
      (.flush os)
      (.encodeToString (Base64/getEncoder) (.toByteArray os)))))
