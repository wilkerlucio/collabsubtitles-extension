(ns swannodette.utils.reactive
  (:refer-clojure :exclude [map mapcat filter remove distinct concat take-while])
  (:require [goog.events :as events]
            [goog.events.EventType]
            [goog.events.FileDropHandler]
            [goog.net.Jsonp]
            [goog.Uri]
            [goog.dom :as gdom]
            collabsubtitles.util
            [cljs.core.async :refer [>! <! chan put! close! timeout alts!]])
  (:require-macros [cljs.core.async.macros :refer [go alt!]]
                   [swannodette.utils.macros :refer [dochan]])
  (:import goog.events.EventType))

(defn log
  ([in] (log nil in))
  ([prefix in]
    (let [out (chan)]
      (dochan [value in]
        (if prefix (collabsubtitles.util/log prefix value)
                   (collabsubtitles.util/log value))
        (>! out value))
      out)))

(def keyword->event-type
  {:keyup     js/goog.events.EventType.KEYUP
   :keydown   js/goog.events.EventType.KEYDOWN
   :keypress  js/goog.events.EventType.KEYPRESS
   :click     js/goog.events.EventType.CLICK
   :dblclick  js/goog.events.EventType.DBLCLICK
   :mousedown js/goog.events.EventType.MOUSEDOWN
   :mouseup   js/goog.events.EventType.MOUSEUP
   :mouseover js/goog.events.EventType.MOUSEOVER
   :mouseout  js/goog.events.EventType.MOUSEOUT
   :mousemove js/goog.events.EventType.MOUSEMOVE
   :focus     js/goog.events.EventType.FOCUS
   :blur      js/goog.events.EventType.BLUR

   :dragstart js/goog.events.EventType.DRAGSTART
   :drag      js/goog.events.EventType.DRAG
   :dragenter js/goog.events.EventType.DRAGENTER
   :dragover  js/goog.events.EventType.DRAGOVER
   :dragleave js/goog.events.EventType.DRAGLEAVE
   :drop      js/goog.events.EventType.DROP
   :dragend   js/goog.events.EventType.DRAGEND})

(defn listen
  ([el type] (listen el type nil))
  ([el type f] (listen el type f (chan)))
  ([el type f out]
    (let [jtype (if (vector? type) (clj->js (cljs.core/map keyword->event-type type))
                                   (keyword->event-type type))]
      (events/listen el jtype
        (fn [e] (when f (f e)) (put! out e))))
    out))

(defprotocol IEventWrapper
  (-source-event [e]))

(extend-protocol IEventWrapper
  js/goog.events.BrowserEvent
  (-source-event [e] (.getBrowserEvent e))

  js/Event
  (-source-event [e] e))

(defn event->files [e]
  (-> (-source-event e)
      .-dataTransfer
      .-files
      prim-seq))

; we need to read the event files direct on the first receiving, because in case it
; gets delayed by core-async the event will lose the file references
(defn listen-file-drop
  ([el] (listen-file-drop el (chan)))
  ([el out & {:keys [concat]
              :or {concat true}}]
    (let [handler (events/FileDropHandler. el true)]
      (events/listen el js/goog.events.FileDropHandler.EventType.DROP
        (fn [e] (let [files (event->files e)]
                  (if concat
                    (doseq [f files] (put! out f))
                    (put! out files))))))
    out))

(defn map [f in]
  (let [out (chan)]
    (go (loop []
          (if-let [x (<! in)]
            (do (>! out (f x))
              (recur))
            (close! out))))
    out))

(defn mapchan
  ([in] (mapchan nil in))
  ([f in]
    (let [out (chan)]
      (go (loop []
            (if-let [x (<! in)]
              (do (>! out (<! (if f (f x) x)))
                (recur))
              (close! out))))
      out)))

(defn mapcat
  ([in] (mapcat nil in))
  ([f in]
    (let [out (chan)]
      (go (loop []
            (if-let [x (<! in)]
              (do
                (loop [y (if f (f x) x)]
                  (when y
                    (>! out (first y))
                    (recur (next y))))
                (recur))
              (close! out))))
      out)))

(defn filter [pred in]
  (let [out (chan)]
    (go (loop []
          (if-let [x (<! in)]
            (do (when (pred x) (>! out x))
              (recur))
            (close! out))))
    out))

(defn remove [f in]
  (let [out (chan)]
    (go (loop []
          (if-let [v (<! in)]
            (do (when-not (f v) (>! out v))
              (recur))
            (close! out))))
    out))

(defn spool [xs]
  (let [out (chan)]
    (go (loop [xs (seq xs)]
          (if xs
            (do (>! out (first xs))
              (recur (next xs)))
            (close! out))))
    out))

(defn split
  ([pred in] (split pred in [(chan) (chan)]))
  ([pred in [out1 out2]]
    (go (loop []
          (if-let [v (<! in)]
            (if (pred v)
              (do (>! out1 v)
                (recur))
              (do (>! out2 v)
                (recur))))))
    [out1 out2]))

(defn concat [xs in]
  (let [out (chan)]
    (go (loop [xs (seq xs)]
          (if xs
            (do (>! out (first xs))
              (recur (next xs)))
            (if-let [x (<! in)]
              (do (>! out x)
                (recur xs))
              (close! out)))))
    out))

(defn distinct [in]
  (let [out (chan)]
    (go (loop [last nil]
          (if-let [x (<! in)]
            (do (when (not= x last) (>! out x))
              (recur x))
            (close! out))))
    out))

(defn fan-in
  ([ins] (fan-in ins (chan)))
  ([ins out]
    (go (loop [ins (vec ins)]
          (when (> (count ins) 0)
            (let [[x in] (alts! ins)]
              (when x
                (>! out x)
                (recur ins))
              (recur (vec (disj (set ins) in))))))
        (close! out))
    out))

(defn take-until
  ([pred-sentinel in] (take-until pred-sentinel in (chan)))
  ([pred-sentinel in out]
    (go (loop []
          (if-let [v (<! in)]
            (do
              (>! out v)
              (if-not (pred-sentinel v)
                (recur)
                (close! out)))
            (close! out))))
    out))

(defn siphon
  ([in] (siphon in []))
  ([in coll]
    (go (loop [coll coll]
          (if-let [v (<! in)]
            (recur (conj coll v))
            coll)))))

(defn always [v c]
  (let [out (chan)]
    (go (loop []
          (if-let [e (<! c)]
            (do (>! out v)
              (recur))
            (close! out))))
    out))

(defn toggle [in]
  (let [out (chan)
        control (chan)]
    (go (loop [on true]
          (recur
            (alt!
              in ([x] (when on (>! out x)) on)
              control ([x] x)))))
    {:chan out
     :control control}))

(defn barrier [cs]
  (go (loop [cs (seq cs) result []]
        (if cs
          (recur (next cs) (conj result (<! (first cs))))
          result))))

(defn cyclic-barrier [cs]
  (let [out (chan)]
    (go (loop []
          (>! out (<! (barrier cs)))
          (recur)))
    out))

(defn jsonp
  ([uri] (jsonp (chan) uri))
  ([c uri]
    (let [gjsonp (goog.net.Jsonp. (goog.Uri. uri))]
      (.send gjsonp nil #(put! c %))
      c)))

(defn throttle*
  ([in msecs]
    (throttle* in msecs (chan)))
  ([in msecs out]
    (throttle* in msecs out (chan)))
  ([in msecs out control]
    (go
      (loop [state ::init last nil cs [in control]]
        (let [[_ _ sync] cs]
          (let [[v sc] (alts! cs)]
            (condp = sc
              in (condp = state
                   ::init (do (>! out v)
                            (>! out [::throttle v])
                            (recur ::throttling last
                              (conj cs (timeout msecs))))
                   ::throttling (do (>! out v)
                                  (recur state v cs)))
              sync (if last
                     (do (>! out [::throttle last])
                       (recur state nil
                         (conj (pop cs) (timeout msecs))))
                     (recur ::init last (pop cs)))
              control (recur ::init nil
                        (if (= (count cs) 3)
                          (pop cs)
                          cs)))))))
    out))

(defn throttle-msg? [x]
  (and (vector? x)
       (= (first x) ::throttle)))

(defn throttle
  ([in msecs] (throttle in msecs (chan)))
  ([in msecs out]
    (->> (throttle* in msecs out)
      (filter #(and (vector? %) (= (first %) ::throttle)))
      (map second))))

(defn debounce
  ([source msecs]
    (debounce (chan) source msecs))
  ([out source msecs]
    (go
      (loop [state ::init cs [source]]
        (let [[_ threshold] cs]
          (let [[v sc] (alts! cs)]
            (condp = sc
              source (condp = state
                       ::init
                         (do (>! out v)
                           (recur ::debouncing
                             (conj cs (timeout msecs))))
                       ::debouncing
                         (recur state
                           (conj (pop cs) (timeout msecs))))
              threshold (recur ::init (pop cs)))))))
    out))
