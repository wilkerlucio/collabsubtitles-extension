(ns collabsubtitles.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [chan <! >! timeout put! close!]]
            [jayq.core :refer [$] :as $]
            [collabsubtitles.srt-parser :refer [parse-srt]]))

(declare find-videos setup-player-integration setup-subtitle-drop jq-event-files
         add-subtitles track-from-file read-file-as-text cues-parser-for-file parse-vtt
         create-cue)

(defn log [& params]
  (.apply (.-log js/console) js/console (clj->js params))
  (last params))

(defn ^:export init []
  (let [videos (find-videos "body")]
    (doseq [video videos]
      (setup-player-integration video))))

(defn find-videos [root]
  (-> ($ root)
      ($/find "video,embed")))

(defn setup-player-integration [video]
  (setup-subtitle-drop video))

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

(defn track-from-file [file]
  (go
    {:cues ((cues-parser-for-file file) (<! (read-file-as-text file)))}))

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

(defn add-subtitles [video {:keys [language cues]}]
  (-> ($ video) ($/add-class "collabsubtitles"))
  (let [track (.addTextTrack video "subtitles" language language)]
    (doseq [cue cues]
      (.addCue track (create-cue cue)))
    (set! (.-mode track) "showing")))

(defn create-cue [{:keys [startTime endTime text]}]
  (js/VTTCue. startTime endTime text))

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

(defn $create-youtube-button []
  (-> ($ "<div>")
      ($/add-class "ytp-button ytp-button-collabsubtitles")
      ($/attr "role" "button")
      ($/attr "aria-label" "CollabSubtitles")
      ($/attr "tabindex" "6500")
      ($/html "CS")))

(defn parse-vtt [string]
  (let [parser (js/WebVTTParser.)]
    (->> (.parse parser string)
         .-cues
         (map (fn [cue] {:startTime (.-startTime cue)
                         :endTime (.-endTime cue)
                         :text (.-text cue)})))))

(defn setup-youtube-player-integration [video]
  (let [$video ($ video)
        $player-container ($/closest $video ".html5-video-player")
        $settings-button ($/find $player-container ".ytp-button.ytp-settings-button")
        $cs-button ($/insert-after ($create-youtube-button) $settings-button)]
    ($/on $cs-button "click" #(go (add-subtitles video (comment "TODO"))))))

(defn jq-event-files [e] (.makeArray js/jQuery (-> e .-originalEvent .-dataTransfer .-files)))

