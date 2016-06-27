(defproject gered/views.honeysql "0.2"
  :description  "HoneySQL view implementation for views"
  :url          "https://github.com/gered/views.honeysql"

  :license      {:name "MIT License"
                 :url "http://opensource.org/licenses/MIT"}

  :dependencies [[gered/views "1.5"]
                 [org.clojure/tools.logging "0.3.1"]]

  :profiles     {:provided
                 {:dependencies
                  [[org.clojure/clojure "1.8.0"]
                   [org.clojure/java.jdbc "0.6.1"]
                   [honeysql "0.6.3"]]}})
