(ns collabsubtitles.formats.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [chan <! >! timeout put! close!]]
            [collabsubtitles.util :refer [log]]
            [collabsubtitles.formats.srt :refer [parse-srt]]
            [collabsubtitles.formats.vtt :refer [parse-vtt]]))

(defmulti cues-parser-for-file
          (fn [file]
            (let [[_ ext] (re-find #"(?i)\.([a-z]+)$" (.-name file))]
              (keyword (.toLowerCase ext)))))

(defmethod cues-parser-for-file :default [file]
                                         (log "can't parse file" file)
                                         (constantly nil))

(defmethod cues-parser-for-file :srt [_] parse-srt)
(defmethod cues-parser-for-file :vtt [_] parse-vtt)

(defn read-file-as-text [file]
  (let [reader (js/FileReader.)
        c (chan)]
    (set! (.-onload reader) (fn [_] (put! c (-> reader .-result)) (close! c)))
    (.readAsText reader file)
    c))

(defn track-from-file [file]
  (go
    {:cues ((cues-parser-for-file file) (<! (read-file-as-text file)))}))
