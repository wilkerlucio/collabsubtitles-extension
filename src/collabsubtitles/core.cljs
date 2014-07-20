(ns collabsubtitles.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [chan <! >! timeout put! close!]]
            [jayq.core :refer [$] :as $]
            collabsubtitles.repl))

(defn log [& params]
  (.apply (.-log js/console) js/console (clj->js params)))

(defn find-videos [root]
  (-> ($ root)
      ($/find "video,embed")))

(def sample-subtitle
  {:language "en"
   :cues [{:begin 0 :end 2 :text "Sample Cue"}
          {:begin 2 :end 999999 :text "This should run for a while"}]})

(defn create-cue [{:keys [begin end text]}]
  (js/VTTCue. begin end text))

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

(defn $delayed-find [$container selector]
  (go-loop []
    (let [res ($/find $container selector)]
      (if (> (.-length res) 0)
        res
        (do
          (<! (timeout 1000))
          (recur))))))

(defn add-subtitles [video {:keys [language cues]}]
  (-> ($ video) ($/add-class "collabsubtitles"))
  (let [track (.addTextTrack video "subtitles" language language)]
    (doseq [cue cues]
      (.addCue track (create-cue cue)))
    (set! (.-mode track) "showing")))

(defn setup-youtube-player-integration [video]
  (go
    (let [$video ($ video)
          $player-container ($/closest $video "#movie_player")
          $settings-button ($/find $player-container ".ytp-button.ytp-settings-button")
          $cs-button ($/insert-after ($create-youtube-button) $settings-button)]
      ($/on $cs-button "click" #(add-subtitles video sample-subtitle)))))

(defn setup-player-integration [video]
  (setup-youtube-player-integration video))

(def subtitle-url "http://www.amara.org/en/subtitles/5Mo4oAj1bxOb/pt-br/32/download/The%20Internets%20Own%20Boy%20The%20Story%20of%20Aaron%20Swartz.pt-br.vtt")

(defn init []
  (go
    (log "async on init" (<! (load-external-url subtitle-url))))
  (let [videos (find-videos "body")]
    (log "found videos" videos)
    (doseq [video videos]
      (setup-player-integration video))))

(defn init-background [])
