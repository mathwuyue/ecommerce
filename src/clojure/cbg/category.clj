(ns cbg.category
  (:use [cbg.template]
        [cbg.const]
        [korma.core]
        [ring.util.response])
  (:require [clojure.tools.logging :as log]
            [cbg.db :as db]
            [cbg.user :as user]
            [cbg.product :as product]))

(defn category-children [parent-id]
  (select db/category (where {:parent parent-id})))

(defn add-category [name parent-id]
  (first (map (fn [e] (second e))
              (insert db/category (values [{:name name :parent parent-id}])))))

(defn remove-category [id]
  (when (empty? (category-children id))
    (do
      (delete db/category (where {:id id}))
      true)))

(defn root-category []
  (first (select db/category (where {:parent nil}))))

(defn category-root-tree
  "dump the category tree from the root category. node.children represent a list of children nodes."
  []
  (let [root (root-category)]
    (defn visit-node [node]
      (let [children (category-children  (:id node))]
        (assoc node :children (map (fn [child] (visit-node child)) children))))
    (visit-node root)))

(defn category-path [node]
  (if (nil? (:parent node))
    []
    (let [parent-node (first (select db/category (where {:id (:parent node)})))]
      (conj (category-path parent-node) node))))

(defn category-editor [req id]
  (user/with-admin req 
    (let [node (first (select db/category (where {:id id})))
        children (category-children id)]
      (render-page "admin/category-editor.html" req {:node node, :children children :products (product/list-product id)}))))

(defn category-editor-root [req]
  (let [root (root-category)]
    (redirect (str "/admin/category/" (:id root) "/"))))

(defn category-rename [req id]
  (user/with-admin req
    (let [node (first (select db/category (where {:id id})))
          newname (:name (:params req))]
      (update db/category (set-fields {:name newname}) (where {:id id}))
      (category-editor req id))))

(defn category-new [req id]
  (user/with-admin req
    (let [new-id (add-category category-default-name id)]
      (log/info "creating category " new-id)
      (redirect (str "/admin/category/" new-id "/")))))

(defn category-remove [req id]
  (user/with-admin req
    (let [node (first (select db/category (where {:id id})))
          parent-id (:parent node)]
      (remove-category id)
      (log/info "removing category " node)
      (redirect (str "/admin/category/" parent-id "/")))))
