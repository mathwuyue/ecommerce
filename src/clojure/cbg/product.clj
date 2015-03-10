(ns cbg.product
  (:use [cbg.template]
        [cbg.const]
        [korma.core]
        [ring.util.response])
  (:require [clojure.tools.logging :as log]
            [clojure.java.io :as io]
            [cbg.db :as db]
            [cbg.user :as user]
            [cbg.indexer :as indexer]))

(defn check-modifiable
  "return values:
  -1, means not modifiable
  0, modifiable
  1, not modifiable, but you can make a copy of this entry and create a new one"
  [product]
  (cond
   (= (:draft product) 1) 0
   (= (:expired product) 1) -1
   :else 1))

(defn list-product [category-id]
  (select db/product (where {:category_id category-id}) (order :publish_date :desc)))

(defn add-product [product-data]
  (first (map (fn [e] (second e))
              (insert db/product (values [product-data])))))

(defn get-product [id]
  (first (select db/product (where {:id id}))))

;; index for searching the products
(def index (indexer/create-index))
(def ^:const index-title-weight 8)
(defn recover-index
  "on app restart, all in memory index are lost, we need to scan the database and re-index everything."
  []
  (log/info "recovering index...")
  (let [all-product (select db/product (where {:draft 0 :expired 0}))]
    (doseq [product all-product]
      (indexer/index-document index (:id product) (:title product) index-title-weight)
      (indexer/index-document index (:id product) (:content product) 1)
      (log/info "recovering product index " (:title product))))
  (log/info "indexer is ready."))

;; (recover-index)

(defn- exclude-param [params]
  (db/exclude-param-input params db/product [:id :see_also]))

(defn- handle-file-update [params]
  (if (:file-upload params)
    (let [{:keys [tempfile filename]} (:file-upload params)]
      (log/info "uploaded " tempfile)
      (loop [counter 0]
        (let [target-file (io/file (str media-directory "/" counter "-" filename))]
          (if (not (.exists target-file))
            (do
              (io/copy tempfile target-file)
              (assoc params :photo (str media-prefix "/" counter "-" filename)))
            (recur (inc counter))))))
    params))

(defn- back-list-page [product]
  (redirect (str "/admin/category/" (:category_id product) "/")))

(defn product-add [req]
  (user/with-admin req
    (let [params (:params req)
          product-data (-> params handle-file-update exclude-param)
          new-id (add-product product-data)]

      (indexer/index-document index new-id (:title product-data) index-title-weight)
      (indexer/index-document index new-id (:content product-data) 1)
      
      (back-list-page product-data))))

(defn product-modify [req id]
  (user/with-admin req
    (log/info req)
    (let [product (get-product id)
          new-product (handle-file-update (exclude-param (:params req)))
          modifiable (check-modifiable product)]
      (cond
       (= modifiable -1)
       (render-page "admin/product-editor.html" req {:modifiable modifiable :product new-product})

       (= modifiable 0)
       (do
         (update db/product (set-fields new-product) (where {:id id}))
         (back-list-page new-product))

       (= modifiable 1)
       (do
         (let [new-id (add-product new-product)]
           (update db/product (set-fields {:expired 1 :see_also new-id}) (where {:id id}))

           (indexer/remove-document index id)
           (indexer/index-document index new-id (:title new-product) index-title-weight)
           (indexer/index-document index new-id (:content new-product) 1)

           (back-list-page new-product)))))))

(defn product-expire [req id]
  (user/with-admin req
    (let [product (get-product id)]
      ;; you can't expire a draft...
      (when (and (= (:draft product) 0) (= (:expired product) 0))
        (do
          (update db/product (set-fields {:expired 1}) (where {:id id}))
          (indexer/remove-document index id)))
      ;; but if you do, that draft got deleted
      (when (= (:draft product) 1)
        (delete db/product (where {:id id})))
      (back-list-page product))))

(defn product-toggle-pin [req id]
  (user/with-admin req
    (let [product (get-product id)]
      ;; sqlite doesn't support boolean, pin is a integer
      (update db/product (set-fields {:pin (- 1 (:pin product))}) (where {:id id}))
      (back-list-page product))))

(defn leaf-category []
  (select cbg.db/category
          (where (not (in :id
                          (subselect "category" (fields :parent) (where (not= :parent nil))))))))

(defn product-editor [req id & category-id]
  (if (nil? id)
    (render-page "admin/product-editor.html" req {:modifiable 0 :product {:category_id category-id} :category-list (leaf-category)})
    (let [product (get-product id)
          modifiable (check-modifiable product)]
      (render-page "admin/product-editor.html" req {:modifiable modifiable :product product :category-list (leaf-category)}))))
