(ns collabsubtitles.background
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [chan <! >! timeout put! close!]]))

(defn log [& params]
  (.apply (.-log js/console) js/console (clj->js params)))

(defn runtime-message-channel []
  (let [c (chan)]
    (-> js/chrome
        .-runtime
        .-onMessage
        (.addListener
          (fn [request sender send-response]
            (let [res-c (chan)]
              (go
                (send-response (pr-str (<! res-c)))
                (close! res-c))
              (put! c {:request (cljs.reader/read-string request)
                       :sender sender
                       :reply-chan res-c})))))
    c))

(defn init []
  (let [c (runtime-message-channel)]
    (go (while true
      (let [{:keys [request reply-chan]} (<! c)]
        (log "got request" request)
        (>! reply-chan "pong"))))))
