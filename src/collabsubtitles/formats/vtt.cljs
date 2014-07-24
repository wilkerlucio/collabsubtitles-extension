(ns collabsubtitles.formats.vtt)

(defn parse-vtt [string]
  (let [parser (js/WebVTTParser.)]
    (->> (.parse parser string)
         .-cues
         (map (fn [cue] {:startTime (.-startTime cue)
                         :endTime (.-endTime cue)
                         :text (.-text cue)})))))
