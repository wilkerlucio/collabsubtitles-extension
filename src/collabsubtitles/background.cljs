(ns collabsubtitles.background
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [chan <! >! timeout put! close! pipe]]
            [ajax.core :as c]))

(defn log [& params]
  (.apply (.-log js/console) js/console (clj->js params)))

(defn ajax-request
  ([uri method]
   (ajax-request uri method {} nil))
  ([uri method opts]
   (ajax-request uri method opts nil))
  ([uri method opts js-ajax]
   (let [data (or (:channel opts) (chan))
         handler (fn [[_ response]] (put! data response))
         opts (assoc opts :handler handler)]
     (c/ajax-request uri method opts js-ajax)
     data)))

(defn runtime-message-channel []
  (let [c (chan)]
    (-> js/chrome .-runtime .-onMessage
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

(defmulti reply-client (fn [x _] (:action x)))

(defmethod reply-client :load-url [{url :url} reply]
  (pipe (ajax-request url :get {:format (c/raw-response-format)})
        reply))

(defn init []
  (let [c (runtime-message-channel)]
    (go (while true
      (let [{:keys [request reply-chan]} (<! c)]
        (reply-client request reply-chan))))))

