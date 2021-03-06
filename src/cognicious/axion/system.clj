(ns cognicious.axion.system
  (:gen-class)
  (:require [aleph.http :as http]
            [byte-streams :as bs]
            [clojure.data.json :as json]
            [clojure.java.data :refer [from-java]]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [cognicious.axion.screenshot :as screen])
  (:import (java.util Properties)
           (oshi SystemInfo)
           (oshi.util EdidUtil)))

(def properties-filename "oshi.json.properties")

(defn make-properties
  "Reads properties files"
  [properties-filename]
  (with-open [is (-> (io/resource properties-filename)
                     (io/input-stream))]
    (doto (Properties.)
      (.load is))))

(defn info-raw 
  "Dispatches all system information using OSHI Properties"
  ([]
   (info-raw (SystemInfo.)))
  ([si]
   (-> (from-java si)
       (assoc :platform (str (oshi.SystemInfo/getCurrentPlatformEnum))))))

(defn usb-recursive-count 
  "Receives info map and counts usb devices"
  [r {:keys [connectedDevices]}]
  (if (= 0 (count connectedDevices))
    (inc r)
    (reduce usb-recursive-count 
            r 
            connectedDevices)))

(defn get-storage 
  "Receives info map and optional store-name and returns a storage info"
  [info storage-name]
  (let [storages (get-in info [:operatingSystem :fileSystem :fileStores])]
    (reduce (fn [r {:keys [name volume totalSpace] :as i}] 
                (if storage-name
                  (if (or (= name storage-name)
                          (= volume storage-name))
                    (reduced i)
                    r)
                  (if (not (zero? totalSpace))
                    (reduced i)
                    r)))
            nil
            storages)))

(defn get-interface [info network-name]
  "Receives info map and optional store-name and returns a network info"
  (let [interfaces (get-in info [:hardware :networkIFs])]
    (reduce (fn [r {:keys [name displayName packetsRecv packetsSent] :as i}] 
                (if network-name
                  (if (or (= name network-name)
                          (= displayName network-name))
                    (reduced i)
                    r)
                  (if (and (not (zero? packetsRecv))
                           (not (zero? packetsSent)))
                    (reduced i)
                    r)))
              nil
              interfaces)))

(defn info 
  "Dispatches tiny version of system information"
  ([]
   (info {}))
  ([{:axn/keys [id storage-default network-default merge-data] :as config}]
   (let [sys-info (SystemInfo.)
         info (info-raw sys-info)
         ;; defaults
         os-storage (get-storage info storage-default)
         net-interface (get-interface info network-default)
         mem-factor (* 1024 1024 1024)
         st-factor (* 1024 1024 1024)
         net-factor (* 1024 1024)]
     (json/write-str
      (merge
       {:id id
        :client-version "0.2.7"
        :uptime (System/currentTimeMillis)
        :os-platform (get-in info [:platform])
        :os-version (get-in info [:operatingSystem :version :version])
        :os-build (get-in info [:operatingSystem :version :buildNumber])
        :os-storage-name (get os-storage :name "Not configured")
        :os-storage-volume (get os-storage :volume)
        :os-storage-usable-space (double (/ (get os-storage :usableSpace 0) st-factor))
        :os-storage-total-space  (double (/ (get os-storage :totalSpace 0)  st-factor))
        :os-storages-open-file-descriptors (get-in info [:operatingSystem :fileSystem :openFileDescriptors])
        :os-storages-max-file-descriptors (get-in info [:operatingSystem :fileSystem :maxFileDescriptors])
        :os-process-count (get-in info [:operatingSystem :processCount])
        :os-thread-count (get-in info [:operatingSystem :threadCount])
        :os-bitness (get-in info [:operatingSystem :bitness])
        :hw-serial-number (get-in info [:hardware :computerSystem :serialNumber])
        :hw-cpu-name (get-in info [:hardware :processor :name])
        :hw-cpu-cores (get-in info [:hardware :processor :logicalProcessorCount])
        :mem-available  (double (/ (get-in info [:hardware :memory :available] 0) mem-factor))
        :mem-total      (double (/ (get-in info [:hardware :memory :total] 0)     mem-factor))
        :mem-swap-total (double (/ (get-in info [:hardware :memory :swapTotal] 0) mem-factor))
        :mem-swap-used  (double (/ (get-in info [:hardware :memory :swapUsed] 0)  mem-factor))
        :net-name (get net-interface :name "Not configured")
        :net-display-name (get net-interface :displayName)
        :net-mac (get net-interface :macaddr)
        :net-ipv4 (first (get net-interface :IPv4addr))
        :net-ipv6 (first (get net-interface :IPv6addr))
        :net-mtu (get net-interface :MTU)
        :net-mbytes-received (double (/ (get net-interface :bytesRecv 0) net-factor))
        :net-mbytes-sent     (double (/ (get net-interface :bytesSent 0) net-factor))
        :net-speed          (double (/ (get net-interface :speed 0)     net-factor))
        :net-error-received (get net-interface :inErrors)
        :net-error-sent (get net-interface :outErrors)
        :net-timestamp (get net-interface :timeStamp)
        :displays-number (count (get-in info [:hardware :displays]))
        :sensor-cpu-temperature (get-in info [:hardware :sensors :cpuTemperature])
        :usb-devices-number (reduce usb-recursive-count
                                    0
                                    (get-in info [:hardware :usbDevices]))}
       merge-data)))))
