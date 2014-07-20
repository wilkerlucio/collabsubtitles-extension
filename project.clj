(defproject collabsubtitles-chrome "0.1.0-SNAPSHOT"
  :description "CollabSubtitles Chrome Extension"
  :url "https://github.com/wilkerlucio/collabsubtitles-extension"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2268"]
                 [org.clojure/core.async "0.1.303.0-886421-alpha"]
                 [jayq "2.5.1"]
                 [cljs-ajax "0.2.6"]]

  :source-paths ["src"]

  :plugins [[lein-cljsbuild "1.0.3"]
            [jarohen/simple-brepl "0.1.1"]]

  :cljsbuild {
    :builds {
      :dev {
        :source-paths ["src"]
        :compiler {
          :output-dir "resources/public/out"
          :output-to "resources/public/main.js"
          :optimizations :whitespace}}

      :release {
        :source-paths ["src"]
        :compiler {
          :output-to "resources/public/main.min.js"
          :optimizations :advanced
          :pretty-print false
          :externs ["externs/jquery-1.9.js" "externs/webvtt.js"]}}}})
