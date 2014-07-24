(ns collabsubtitles.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [chan <! >! timeout put! close!]]
            [jayq.core :refer [$] :as $]
            [collabsubtitles.players.core :refer [setup-subtitle-drop]]
            [collabsubtitles.players.youtube :as youtube]))

(defn find-videos [root]
  (-> ($ root)
      ($/find "video,embed")))

(defn setup-player-integration [video]
  (setup-subtitle-drop video)
  (youtube/setup-player-integration video))

(defn ^:export init []
  (let [videos (find-videos "body")]
    (doseq [video videos]
      (setup-player-integration video))))
