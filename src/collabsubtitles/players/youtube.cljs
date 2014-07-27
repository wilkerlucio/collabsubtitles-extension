(ns collabsubtitles.players.youtube
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                   [swannodette.utils.macros :refer [dochan]])
  (:require [swannodette.utils.dom :as dom]
            [swannodette.utils.reactive :as r]
            [collabsubtitles.util :refer [log load-external-url]]
            [collabsubtitles.players.core :refer [add-subtitles]]
            [collabsubtitles.formats.srt :refer [parse-srt]]
            [cljs.core.async :refer [>! <! chan put! close! timeout alts!]]))

(defn create-button []
  (doto (dom/create-element! "div")
        (dom/add-class! "ytp-button ytp-button-collabsubtitles")
        (dom/set-properties! {:role "button"
                              :aria-label "CollabSubtitles"
                              :tabindex "6500"})
        (dom/set-html! "CS")))

(defn create-dialog []
  (doto (dom/create-element! "select")
        (dom/add-class! "collabsubtitles-selector")
        (dom/set-css! :display "none")
        (dom/append! (dom/create-option! :title "Loading"))))

(defn add-button-on-player [video button]
  (if-let [ref-button (some->> (dom/ancestor video (dom/query-matcher ".html5-video-player"))
                               (dom/by-query ".ytp-button.ytp-settings-button"))]
    (dom/insert-after button ref-button)))

(defn track->option [{:keys [download-url name :as origin]}]
  (dom/create-option! :title name
                      :value download-url))

(defn external-edn [url]
  (r/map cljs.reader/read-string (load-external-url url)))

(defn external-srt [url]
  (r/map (comp parse-srt cljs.reader/read-string) (load-external-url url)))

(defn search-tracks [video-id]
  (external-edn (str "http://localhost:3001/youtube/"
                     video-id)))

(defn load-tracks-on-select [select video-id out]
  (go (let [tracks (<! (search-tracks video-id))]
         (dom/set-html! select "")
         (dom/append! select (dom/create-option! :title "Select Language"))
         (dom/append-all! select (map track->option tracks))

         (dochan [_ (dom/listen select :change)]
                 (if-let [url (dom/value select)]
                   (let [cues (<! (external-srt url))]
                     (>! out {:cues cues})))))))

(defn setup-player-integration [video]
  (let [out (chan)]
    (if-let [video-id (dom/data video :youtube-id)]
      (let [button (add-button-on-player video (create-button))
            select (dom/insert-before (create-dialog) button)
            tc (->> (dom/listen button :click)
                    (r/once #(load-tracks-on-select select video-id out)))]
        (go-loop [opened false]
          (when-let [_ (<! tc)]
            (if opened (dom/hide select)
                       (dom/show select))
            (recur (not opened)))))
      (close! out))
    out))
