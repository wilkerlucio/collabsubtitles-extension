(ns collabsubtitles.players.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [chan <! >! timeout put! close!]]
            [jayq.core :refer [$] :as $]
            [collabsubtitles.formats.core :refer [track-from-file]]))

(defn jq-event-files [e] (.makeArray js/jQuery (-> e .-originalEvent .-dataTransfer .-files)))

(defn create-cue [{:keys [startTime endTime text]}]
  (js/VTTCue. startTime endTime text))

(defn add-subtitles [video {:keys [language cues]}]
  (-> ($ video) ($/add-class "collabsubtitles"))
  (let [track (.addTextTrack video "subtitles" language language)]
    (doseq [cue cues]
      (.addCue track (create-cue cue)))
    (set! (.-mode track) "showing")))

(defn setup-subtitle-drop [video]
  (let [$video ($ video)
        add-class (partial $/add-class $video "collabsubtitles-dragover")
        remove-class (partial $/remove-class $video "collabsubtitles-dragover")]
    (doto $video
      ($/on "dragenter dragover" #(do (add-class) false))
      ($/on "dragleave dragend" remove-class)
      ($/on "drop" (fn [e]
                     (remove-class)
                     (doseq [file (jq-event-files e)]
                       (go
                         (add-subtitles video (<! (track-from-file file)))))
                     false)))))
