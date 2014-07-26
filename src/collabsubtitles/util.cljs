(ns collabsubtitles.util
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [chan <! >! timeout put! close!]]))

(defn log [& params]
  (.apply (.-log js/console) js/console (clj->js params))
  (last params))

(defn chrome-send-message [message]
  (let [c (chan)]
    (-> js/chrome
        .-runtime
        (.sendMessage
          (pr-str message)
          (fn [response]
            (put! c (cljs.reader/read-string response))
            (close! c))))
    c))

(defn load-external-url [url]
  (chrome-send-message {:action :load-url
                        :url url}))

(defn file->string
  ([file] (file->string file (chan)))
  ([file out]
   (let [reader (js/FileReader.)]
     (set! (.-onload reader) (fn [_] (put! out (-> reader .-result))))
     (.readAsText reader file)
     out)))
