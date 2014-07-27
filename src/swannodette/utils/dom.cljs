(ns swannodette.utils.dom
  (:require-macros [cljs.core.async.macros :refer [go alt!]])
  (:require [goog.events :as events]
            [goog.events.EventType]
            [goog.events.FileDropHandler]
            [goog.style :as style]
            [goog.dom :as dom]
            [goog.dom.classes :as classes]
            [goog.dom.dataset :as dataset]
            [collabsubtitles.util :refer [log]]
            [cljs.core.async :refer [>! <! chan put! close! timeout alts!]]))

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
   :change    js/goog.events.EventType.CHANGE

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
    (let [jtype (if (vector? type) (clj->js (map keyword->event-type type))
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

(defn insert-after [el target]
  (doto el (dom/insertSiblingAfter target)))

(defn insert-before [el target]
  (doto el (dom/insertSiblingBefore target)))

(defn append! [parent el]
  (doto parent (dom/append el)))

(defn append-all! [parent els]
  (doseq [el els] (append! parent el))
  parent)

(defn all-by-query
  ([query] (all-by-query query (.-body js/document)))
  ([query root] (prim-seq (.querySelectorAll root query))))

(defn by-query
  ([query] (by-query query (.-body js/document)))
  ([query root] (.querySelector root query)))

(defn by-id [id]
  (.getElementById js/document id))

(defn set-html! [el s]
  (set! (.-innerHTML el) s))

(defn set-text! [el s]
  (dom/setTextContent el s))

(defn set-class! [el name]
  (classes/set el name))

(defn set-properties! [el properties]
  (dom/setProperties properties))

(defn set-css! [el n v]
  (style/setStyle el (name n) v))

(defn add-class! [el name]
  (classes/add el name))

(defn remove-class! [el name]
  (classes/remove el name))

(defn has-class? [el name]
  (classes/has el name))

(defn create-element! [name]
  (dom/createElement name))

(defn ancestor [el matcher]
  (dom/getAncestor el matcher))

(defn tag-match [tag]
  (fn [el]
    (when-let [tag-name (.-tagName el)]
      (= tag (.toLowerCase tag-name)))))

(defn parent [el tag]
  (let [matcher (tag-match tag)]
    (if (matcher el)
      el
      (dom/getAncestor el (tag-match tag)))))

(defn el-matcher [el]
  (fn [other] (identical? other el)))

(defn query-matcher [query]
  (fn [el]
    (if (and el (.-matches el)) (.matches el query))))

(defn by-tag-name [el tag]
  (prim-seq (dom/getElementsByTagNameAndClass tag nil el)))

(defn offset [el]
  [(style/getPageOffsetLeft el) (style/getPageOffsetTop el)])

(defn in? [e el]
  (let [target (.-target e)]
    (or (identical? target el)
        (not (nil? (dom/getAncestor target (el-matcher el)))))))

(defn- dash-to-camel [method-name]
  (clojure.string/replace method-name #"-(\w)"
                          #(clojure.string/upper-case (second %1))))

(defn data [e n]
  (dataset/get e (dash-to-camel (name n))))

(defn set-data! [e n v]
  (dataset/set e (dash-to-camel (name n)) v))

(defprotocol IHighlightable
  (-highlight [el])
  (-unhighlight [el]))

(defn highlight-on-dragging-over [element]
  (listen element [:dragenter :dragover] #(-highlight element))
  (listen element [:dragleave :dragend :drop] #(-unhighlight element)))

(defn create-option! [& {:keys [title value]}]
  (doto (create-element! "option")
        (aset "value" value)
        (set-html! title)))

(defn hide [el]
  (style/setElementShown el false))

(defn show [el]
  (style/setElementShown el true))

(defn value [el]
  (.-value el))
