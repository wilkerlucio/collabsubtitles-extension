(ns collabsubtitles.srt-parser
  (:require [clojure.string :as str]))

(declare read-cue read-id read-time read-content parse-times convert-time
         strip-ssa escape-html unescaped-allowed-html ssa-nl-to-br)

(defn parse-srt [string]
  (loop [lines (str/split-lines string)
         cues []]
    (let [{:keys [cue lines]} (->> lines
                                   (drop-while str/blank?)
                                   read-cue)]
      (if (seq lines)
        (recur lines (conj cues cue))
        (conj cues cue)))))

(defn read-cue [lines]
  (if (seq lines)
    (-> {:lines lines}
        read-id
        read-time
        read-content)))

(defn read-id [{:keys [lines] :as source}]
  (-> source
      (assoc-in [:cue :id] (js/parseInt (first lines)))
      (update-in [:lines] rest)))

(defn read-time [{:keys [lines] :as source}]
  (-> source
      (update-in [:cue] #(merge % (parse-times(first lines))))
      (update-in [:lines] rest)))

(defn parse-times [string]
  (if-let [numbers (re-find #"(\d{2}):(\d{2}):(\d{2})[,.](\d{3})\s+-->\s+(\d{2}):(\d{2}):(\d{2})[,.](\d{3})" string)]
    {:startTime (apply convert-time (subvec numbers 1 5))
     :endTime (apply convert-time (subvec numbers 5 9))}
    {}))

(defn convert-time [h m s mm]
  (let [hours (* (js/parseInt h) 3600)
        minutes (* (js/parseInt m) 60)
        seconds (js/parseInt s)
        milliseconds (/ (js/parseInt mm) 1000)]
    (+ hours minutes seconds milliseconds)))

;; most of the string sanitization code was ported from Mozilla's Popcorn.js project
;; which can be found here: https://github.com/mozilla/popcorn-js/blob/master/parsers/parserSRT/popcorn.parserSRT.js
(defn read-content [{:keys [lines] :as source}]
  (let [present? (complement str/blank?)
        text-lines (->> (take-while present? lines)
                        (str/join "\\N")
                        strip-ssa
                        escape-html
                        unescaped-allowed-html
                        ssa-nl-to-br)]
    (-> source
        (assoc-in [:cue :text] text-lines)
        (update-in [:lines] #(drop-while present? %)))))

(defn strip-ssa [string]
  (str/replace string #"(?i)\{(\\[\w]+\(?([\w\d]+,?)+\)?)+\}" ""))

(defn escape-html [string]
  (-> string
      (str/replace #"<" "&lt;")
      (str/replace #">" "&gt;")))

(defn unescaped-allowed-html [string]
  (str/replace string
               #"(?i)&lt;(/?(font|b|u|i|s))((\s+(\w|\w[\w\-]*\w)(\s*=\s*(?:\".*?\"|'.*?'|[^'\">\s]+))?)+\s*|\s*)(/?)&gt;"
               "<$1$3$7>"))

(defn ssa-nl-to-br [string]
  (str/replace string #"\\N" "<br />"))
