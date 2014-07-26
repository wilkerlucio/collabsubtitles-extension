(ns collabsubtitles.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                   [swannodette.utils.macros :refer [dochan]])
  (:require [cljs.core.async :refer [chan <! >! timeout put! close!]]
            [collabsubtitles.players.core :refer [add-subtitles]]
            [collabsubtitles.players.youtube :as youtube]
            [collabsubtitles.formats.core :refer [file->track]]
            [swannodette.utils.reactive :as r]
            [swannodette.utils.dom :as dom]))

(defn find-videos [root]
  (dom/all-by-query "video,embed" root))

(defn filedrop->track [element]
  (->> (r/listen-file-drop element)
       (r/mapchan file->track)))

(defprotocol IHighlightable
  (-highlight [el])
  (-unhighlight [el]))

(extend-protocol IHighlightable
  js/HTMLVideoElement
  (-highlight [el] (dom/add-class! el "collabsubtitles-dragover"))
  (-unhighlight [el] (dom/remove-class! el "collabsubtitles-dragover")))

(defn highlight-on-dragging-over [element]
  (r/listen element [:dragenter :dragover] #(-highlight element))
  (r/listen element [:dragleave :dragend :drop] #(-unhighlight element)))

(defn player-integration [video-chan]
  (dochan [video video-chan]
    (highlight-on-dragging-over video)
    (dochan [track (->> (filedrop->track video)
                        (r/remove #(= :no-parser %))
                        (r/log "track"))]
      (add-subtitles video track))))

(defn ^:export init []
  (let [video-chan (r/spool (find-videos (.-body js/document)))
        integrator (r/log "player integration" (player-integration video-chan))]
    (go (while true (<! integrator)))))
