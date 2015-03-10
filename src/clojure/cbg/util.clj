(ns cbg.util
  (:use [korma.core])
  (:require [cbg.db :as db]
            [cbg.product :as product]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [clojure.java.io :as io]))

(defn import-category [file-name]
  (defn is-sub [raw-name]
    (.startsWith raw-name "\t"))
  (defn sub-to-real [raw-name]
    (.substring raw-name 1))
  
  (with-local-vars [local-last-parent-id 1]
    (defn parse-and-import [line]
      ;; (log/debug "parsing line " line)
      (let [[category-text-id category-raw-name] (str/split line #": ")
            category-id (+ 2 (Integer/parseInt category-text-id))
            [category-name category-parent] (if (is-sub category-raw-name)
                                              [(sub-to-real category-raw-name) @local-last-parent-id]
                                              (do
                                                (var-set local-last-parent-id category-id)
                                                [category-raw-name 1]))]
        (log/info "importing category " category-id ", " category-name ", " category-parent)
        (insert db/category (values [{:id category-id :name category-name :parent category-parent}]))))

    (with-open [f (io/reader file-name)]
      (doseq [line (line-seq f)]
        (if-not (empty? line)
          (parse-and-import line))))))

(defn import-item [category-id file-name image-file-name]

  (defn read-item-list []
    (let [item-list (transient [])
          content (new StringBuilder)]
      (with-open [f (io/reader file-name)]
        (log/info "importing from item file " file-name)
        (doseq [line (line-seq f)]
          (if (= line "###")
            (do
              (conj! item-list (str content))
              (. content (setLength 0)))
            (. content (append line)))))
      (persistent! item-list)))

    (let [item-list (read-item-list)]
      (log/info "title: " (get item-list 0))
      (insert db/product (values [{:category_id category-id
                                   :title (get item-list 0)
                                   :content (get item-list 1)
                                   :photo image-file-name
                                   :external_link (get item-list 2 "")
                                   :external_purchase ""}]))))

(defn discover-local-categories []
  (map (fn [category] (- (:id category) 2)) (product/leaf-category)))

(defn discover-local-products [category-id]
  (let [dir (io/file (str "./src/webcrawler/cat/" category-id))]
    (->> (take 20 (reverse (sort (map #(.getName %) (.listFiles dir))))) (into []))))

(defn local-product-image [local-id]
  (str "/media/" local-id ".jpg"))

(defn local-product-file [category-id local-id]
  (str "./src/webcrawler/cat/" category-id "/" local-id))

(defn import-all []
  (doseq [category-id (discover-local-categories)]
    (log/info "discovered category " category-id)
    (doseq [product-id (discover-local-products category-id)]
      (log/info "discover product " product-id)
      (let [image-path (local-product-image product-id)
            product-path (local-product-file category-id product-id)]
        (import-item (+ category-id 2) product-path image-path)))))

