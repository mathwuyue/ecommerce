(defproject cbg "0.1.0-SNAPSHOT"
  :description "cbg web backend"
  :url "http://example.com/FIXME"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [compojure "1.1.8"]
                 [org.clojure/tools.logging "0.3.0"]
                 [ring/ring-jetty-adapter "1.3.0"]
                 [digest "1.4.4"]
                 [korma "0.3.0"]
                 [selmer "0.6.9"]
                 [org.xerial/sqlite-jdbc "3.7.15-M1"]
                 [mysql/mysql-connector-java "5.1.6"]

                 ;; HTMP parser
                 [clj-soup/clojure-soup "0.1.2"]
                 
                 ;; NLP tools
                 [stemmers "0.2.2"]
                 [com.guokr/clj-cn-nlp "0.2.1"]]

  :plugins [[lein-ring "0.8.11"]
            [cider/cider-nrepl "0.8.1"]]

  :jvm-opts ["-Xmx1g" "-server"]
  :ring {:handler cbg.handler/app}
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring-mock "0.1.5"]]}})
