(ns cbg.db 
  (:use [korma.db]
        [korma.core])
  (:require [clojure.string :as str]
            [clojure.java.jdbc :as sql]
            [clojure.tools.logging :as log]))

;; copied from http://www.cnblogs.com/ilovewindy/p/3851417.html
(defn mysql-utf8
  [{:keys [host port db make-pool?]
    :or {host "localhost", port 3306, db "", make-pool? true}
    :as opts}]
  (merge {:classname "com.mysql.jdbc.Driver" ; must be in classpath
          :subprotocol "mysql"
          :subname (str "//" host ":" port "/" db "?useUnicode=true&characterEncoding=UTF-8")
          :delimiters "`"
          :make-pool? make-pool?}
         opts))

;; (def db-spec (mysql-utf8 {:host "rdszv2aijzv2aij.mysql.rds.aliyuncs.com" :db "cbg" :user "panex" :password "wqt0ae9pt_93n"}))
(def db-spec (sqlite3 {:db "resources/db/devdb.sqlite3"}))

(defdb db db-spec)

(declare users)

(defentity users
  (entity-fields :id :username :password_hash :email :real_name :phone :country :province :city :address :zipcode :id_number))

(defentity category
  (entity-fields :id :name :parent))

(belongs-to category category {:fk :parent})

(defentity product
  (entity-fields :id :title :content :photo :external_purchase :external_link :draft :expired :see_also :pin :click_count :publish_date :category_id)
  (belongs-to category))

(defn init-db-content []
  (insert category (values [{:name "root" :parent nil}])))

(defn drop-tables []
  (do
    (sql/db-do-commands db-spec
                        (sql/drop-table-ddl "users")
                        (sql/drop-table-ddl "category")
                        (sql/drop-table-ddl "product"))))

(defn create-tables []
  (do
    (sql/db-do-commands db-spec
                        (sql/create-table-ddl
                         "users"
                         [:id "INTEGER PRIMARY KEY"]
                         [:username "VARCHAR(64)"]
                         [:password_hash "VARCHAR(128)"]
                         [:email "VARCHAR(32)"]
                         [:real_name "VARCHAR(64)"]
                         [:phone "VARCHAR(16)"]
                         [:country "VARCHAR(32)"]
                         [:province "VARCHAR(16)"]
                         [:city "VARCHAR(16)"]
                         [:address "VARCHAR(128)"]
                         [:zipcode "VARCHAR(12)"]
                         [:id_number "VARCHAR(64)"])
                        (sql/create-table-ddl
                         "category"
                         [:id "INTEGER PRIMARY KEY"]
                         [:name "VARCHAR(256)"]
                         [:parent "INTEGER"]
                         
                         ["FOREIGN KEY (parent) REFERENCES category(id)"])

                        (sql/create-table-ddl
                         "product"
                         [:id "INTEGER PRIMARY KEY"]
                         [:category_id "INTEGER NOT NULL"]
                         [:title "VARCHAR(1024) NOT NULL"]
                         [:content "TEXT NOT NULL"]
                         [:photo "VARCHAR(256)"] ;; photo url
                         [:external_purchase "VARCHAR(256)"] ;; taobao link
                         [:external_link "VARCHAR(256) NOT NULL"] ;; original link
                         ;; states of this product:
                         ;; draft:   unpublished, search engine won't find this item.
                         ;; expired: product expired, search engine indexed before, but won't find
                         ;;          this item any more. Inside the search engine data structure,
                         ;;          states of this product id are inconsistent.
                         [:draft "INTEGER DEFAULT 1"]
                         [:expired "INTEGER DEFAULT 0"]
                         [:pin "INTEGER DEFAULT 0"]
                         [:click_count "INTEGER DEFAULT 0"]
                         [:publish_date "TIMESTAMP DEFAULT CURRENT_TIMESTAMP"]
                         [:see_also "INTEGER"]

                         ["FOREIGN KEY (see_also) REFERENCES product(id)"]
                         ["FOREIGN KEY (category_id) REFERENCES category(id)"]))))

;; TODO: tables for SSO, product info
;; SSO table should keep a mapping from some of or all QQ, wechat, weibo account to user foreign keys

(defn filter-param-input [params keyset]
  (->> (filter (fn [pair] (contains? keyset (first pair))) params) (into {})))

(defn exclude-param-input [params model exclude-keyseq]
  (filter-param-input params (apply disj (set (:fields model)) exclude-keyseq)))
