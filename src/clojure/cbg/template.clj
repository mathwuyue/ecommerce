(ns cbg.template
  (:use [selmer.parser])
  (:require [clojure.string :as str]))

(cache-off!)

(def templates-prefix "templates/")

(defn render-page [filename request map]
  (render-file (str templates-prefix filename) (assoc map :session (:session request))))

(defn render-page-with-category-tree [filename request map root]
  (render-page filename request (assoc map :root root)))
