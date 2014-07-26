(ns collabsubtitles.players.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [chan <! >! timeout put! close!]]
            [swannodette.utils.dom :as dom]
            [collabsubtitles.formats.core :refer [file->track]]))

(defn create-cue [{:keys [startTime endTime text]}]
  (js/VTTCue. startTime endTime text))

(defn add-subtitles [video {:keys [language cues]}]
  (dom/add-class! video "collabsubtitles")
  (let [track (.addTextTrack video "subtitles" language language)]
    (doseq [cue cues]
      (.addCue track (create-cue cue)))
    (set! (.-mode track) "showing")))
