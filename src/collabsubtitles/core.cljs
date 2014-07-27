(ns collabsubtitles.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                   [swannodette.utils.macros :refer [dochan]])
  (:require [cljs.core.async :refer [chan <! >! timeout put! close!]]
            [collabsubtitles.players.core :refer [add-subtitles]]
            [collabsubtitles.players.youtube :as youtube]
            [collabsubtitles.formats.core :refer [file->track]]
            [collabsubtitles.util :refer [log]]
            [swannodette.utils.reactive :as r]
            [swannodette.utils.dom :as dom]))

(defn find-videos [root]
  (dom/all-by-query "video" root))

(extend-protocol dom/IHighlightable
  js/HTMLVideoElement
  (-highlight [el] (dom/add-class! el "collabsubtitles-dragover"))
  (-unhighlight [el] (dom/remove-class! el "collabsubtitles-dragover")))

(defn filedrop->track [element]
  (dom/highlight-on-dragging-over element)
  (->> (dom/listen-file-drop element)
       (r/mapchan file->track)))

(defn player-integration [video-chan]
  (dochan [video video-chan]
    (let [drop-tracks (filedrop->track video)
          integration-tracks (youtube/setup-player-integration video)]
      (dochan [track (->> (r/fan-in [drop-tracks integration-tracks])
                          (r/remove #(= :no-parser %)))]
              (add-subtitles video track)))))

(defn ^:export init []
  (let [video-chan (r/spool (find-videos (.-body js/document)))
        integrator (player-integration video-chan)]
    (dochan [_ integrator])))
