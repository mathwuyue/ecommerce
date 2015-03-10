(ns cbg.handler
  (:use [ring.adapter.jetty :only [run-jetty]]
        [clojure.tools.namespace.repl :only [refresh]]
        [cbg.user]
        [cbg.const]
        [cbg.category]
        [cbg.product])
  (:require [compojure.core :refer :all]
            [cbg.views :as views]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [ring.middleware.session :as session]
            [ring.middleware.session.cookie :as session-cookie]
            [ring.middleware.params :as params]
            [ring.middleware.keyword-params :as keyword-params]
            [ring.middleware.multipart-params :as multipart]
            [clojure.tools.logging :as log]))

(defroutes app-routes
  (GET "/" [] views/home)
  (GET "/search/" [] views/search)
  (GET "/category/:id/" [:as req id] (views/home req id))
  (GET "/detail/:id/" [:as req id] (views/detail req id))
  
  (GET "/user/login/" [] user-login)
  (POST "/user/login/" [] user-login-post)
  (GET "/user/logout" [] user-logout)
  
  (GET "/user/register/" [] user-register)
  (POST "/user/register/" [] user-register-post)

  (GET "/user/info/" [] user-info)
  (POST "/user/info/" [] user-info-post)

  (GET "/admin/category/:id/" [:as req id] (category-editor req id))
  (POST "/admin/category/:id/" [:as req id] (category-rename req id))
  (GET "/admin/category/:id/new/" [:as req id] (category-new req id))
  (GET "/admin/category/:id/remove/" [:as req id] (category-remove req id))
  (GET "/admin/category/" [] category-editor-root)

  (GET "/admin/category/:id/products/new/" [:as req id] (product-editor req nil id))
  (POST "/admin/category/:id/products/new/" [:as req id] (product-add req))

  (GET "/admin/products/:id/modify/" [:as req id] (product-editor req id))
  (POST "/admin/products/:id/modify/" [:as req id] (product-modify req id))
  (GET "/admin/products/:id/expire/" [:as req id] (product-expire req id))
  (GET "/admin/products/:id/toogle-pin/" [:as req id] (product-toggle-pin req id))

  (route/files media-prefix {:root media-directory})

  (route/resources "/")
  (route/not-found "Not Found"))

(def app
  (-> app-routes (session/wrap-session {:store (session-cookie/cookie-store)}) keyword-params/wrap-keyword-params params/wrap-params multipart/wrap-multipart-params))

(def server nil)

(defn start-server []
  (alter-var-root #'server (fn [s] (run-jetty app {:port 3000 :join? false :auto-reload? true}))))

(defn stop-server []
  (when server
    (-> server .stop)))

(defn reset []
  (stop-server)
  (use '[cbg.handler] :reload-all)
  (start-server))
