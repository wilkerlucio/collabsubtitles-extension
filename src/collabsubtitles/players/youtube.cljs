(ns collabsubtitles.players.youtube
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [jayq.core :refer [$] :as $]
            [collabsubtitles.util :refer [log]]
            [collabsubtitles.players.core :refer [add-subtitles]]))

(defn $create-button []
  (doto ($ "<div>")
        ($/add-class "ytp-button ytp-button-collabsubtitles")
        ($/attr "role" "button")
        ($/attr "aria-label" "CollabSubtitles")
        ($/attr "tabindex" "6500")
        ($/html "CS")))

(defn $add-button-on-player [video button]
  (let [$video ($ video)
        $player-container ($/closest $video ".html5-video-player")
        $settings-button ($/find $player-container ".ytp-button.ytp-settings-button")]
    ($/insert-after button $settings-button)))

(defn setup-player-integration [video]
  (doto ($add-button-on-player video ($create-button))
        ($/on "click" #(go (add-subtitles video (partial log "TODO"))))))
