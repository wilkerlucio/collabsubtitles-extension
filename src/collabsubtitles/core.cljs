(ns collabsubtitles.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [chan <! >! timeout]]
            [jayq.core :refer [$ css html]]
            collabsubtitles.repl))

(enable-console-print!)

(defn init []
  (print "Hello World!"))
