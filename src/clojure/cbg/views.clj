(ns cbg.views
  (:use [cbg.template]
        [cbg.const]
        [korma.core]
        [korma.db]
        [ring.util.response])
  (:require [clojure.tools.logging :as log]
            [clojure.java.io :as io]
            [cbg.db :as db]
            [cbg.user :as user]
            [cbg.product :as product]
            [cbg.category :as category]
            [cbg.indexer :as indexer]))

(defn- page [query p]
  (-> query (offset (* (dec p) item-per-page)) (limit item-per-page) (select)))

(defn- requested-page [req]
  (Integer/parseInt (get (:params req) :page "1")))

(defn- total-page [count]
  (int (Math/ceil (/ count (float item-per-page)))))

(defn- count-page [query]
  (total-page (:cnt (first (-> query (aggregate (count :id) :cnt) (select))))))

(defn- page-range [cur-page max-page]
  (let [start (max 1 (- cur-page 2))
        end (min (inc max-page) (+ start 5))]
    (range start end)))

(defn- base-map [var-map]
  (assoc var-map
    :major-category (:children (category/category-root-tree))))

(defn home [req & category-id]
  ;; set of queries
  (def pinned-products
    (-> (select* db/product)
        (where {:draft 0 :expired 0})
        (order :pin :desc)
        (order :publish_date :desc)))

  (def newest-products
    (-> (select* db/product)
        (where {:draft 0 :expired 0})
        (order :publish_date :desc)))

  (defn filter-by-category [query]
    (if (nil? category-id)
      query
      (-> query (where {:category_id category-id}))))

  (def category-path
    (if-not (nil? category-id)
      (category/category-path (first (select db/category (where {:id category-id}))))))

  (def query-map {"pinned" pinned-products "newest" newest-products})

  (let [query-mode (get (:params req) :mode "pinned")
        query (filter-by-category (get query-map query-mode))
        cur-page (requested-page req)
        products (-> query (page cur-page))
        total-page (-> query count-page)]
    (render-page "home.html" req (base-map {:products products
                                            :category-path category-path
                                            :query-mode query-mode
                                            :cur-page cur-page
                                            :page-range (page-range cur-page total-page)
                                            :max-page total-page}))))

(defn search [req]
  ;; (log/info req)
  (let [query (:query (:params req))]
    (if (nil? query)
      (render-page "search.html" req (base-map {:init true}))
      (let [cur-page (requested-page req)
            doc-score (indexer/query-index product/index query)
            total-count (count doc-score)
            total-page (int (Math/ceil (/ total-count (float item-per-page))))
            score-doc (sort (map (fn [p] [(second p) (first p)]) doc-score))
            product-ids (map
                         second
                         (subvec (->> (sort score-doc) vec)
                                 (* (dec cur-page) item-per-page)
                                 (min (* cur-page item-per-page) total-count)))
            products (select db/product (where (in :id product-ids)))]
        (render-page "search.html" req (base-map {:products products
                                                  :cur-page cur-page
                                                  :page-range (page-range cur-page total-page)
                                                  :max-page total-page}))))))

(defn detail [req id]
  (let [product
        (transaction
         (let [[product] (select db/product (where {:id id}))]
           (update db/product (set-fields {:click_count (inc (:click_count product))}) (where {:id id}))
           product))]
    (render-page "detail.html" req (base-map {:product product}))))
