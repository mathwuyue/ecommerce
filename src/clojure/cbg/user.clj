(ns cbg.user
  (:use [cbg.template]
        [cbg.const]
        [korma.core]
        [ring.util.response])
  (:require [clojure.tools.logging :as log]
            [digest]
            [cbg.db :as db]))

(defn- do-password-hash [password]
  (digest/sha1 (str "s1&F^87" password)))

(defn- do-register-checks [params]
  (let [username (:username params)
        password (:password params)
        email (:email params)]
    (cond (nil? username)
          {:error {:username user-name-required}}

          (nil? password)
          {:error {:password user-password-required}}
          
          (nil? email)
          {:error {:email user-email-required}}

          (nil? (re-matches #"[A-Za-z0-9.-_]+@[A-Z0-9a-z.]+" email))
          {:error {:email user-field-invalid}}
          
          (> (count username) 20)
          {:error {:username user-name-too-long}}

          (not (nil? (first (select db/users (where {:username username})))))
          {:error {:username user-already-exist}}

          nil
          nil
          )))

(defn user-register [req]
  (log/info req)
  (render-page "register.html" req {}))

(defn user-register-post [req]
  (let [params (:params req)
        error (do-register-checks params)]
    (if (nil? error)
      (let [username (:username params)
            phash (do-password-hash (:password params))
            email (:email params)]
        (insert db/users (values [{:username username :password_hash phash :email email}]))
        (let [user (first (select db/users (where {:username username :password_hash phash})))]
          (-> (redirect "/") (assoc :session {:user user}))))
      (do
        (log/info "bad user registration info")
        (render-page "register.html" req (merge error params))))))

(defn- do-login-checks [req]
  (let* [username (:username (:params req))
         password (:password (:params req))
         phash (do-password-hash password)]
         (first (select db/users (where {:username username :password_hash phash})))))

(defn user-login [req]
  (render-page "login.html" req {}))

(defn user-login-post [req]
  (let [user (do-login-checks req)]
    (if user
      ;; redirect to homepage
      (-> (redirect "/") (assoc :session {:user user}))
      (render-page "login.html" req {:error user-login-failed}))))

(defn user-logout [req]
  (-> (redirect "/") (dissoc :session)))

(defmacro with-user-login [req & forms]
  `(if (nil? (:user (:session ~req)))
     (redirect "/user/login/")
     ~(cons 'do forms)))

(defn user-info [req]
  (with-user-login req
    (render-page "userinfo.html" req {:user (:user (:session req))})))

(defn user-info-post [req]
  (with-user-login req
    ;; update user information
    ;; TODO: validation!
    (let [user (:user (:session req))
          ;; keys (keys (dissoc user :id :username :password_hash)) ;; important, these stuffs can't be modified
          ;; newuser (apply assoc {} (apply concat (map (fn [k] [k (get (:params req) k)]) keys)))]
          newuser (db/exclude-param-input user db/users [:id :username :password_hash])]
      (update db/users (set-fields newuser) (where {:id (:id user)}))
      (-> (redirect "/") (assoc :session {:user newuser})))))

(defmacro with-admin [req & forms]
  `(let [u# (:user (:session ~req))]
     (if (and u# (= (:username u#) "admin"))
       ~(cons 'do forms)
       (not-found (str "Sorry " u# ", Not Found")))))
