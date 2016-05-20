(ns views.honeysql.view
  (:require
    [views.protocols :refer :all]
    [views.honeysql.core :refer [hint-type]]
    [views.honeysql.util :refer [query-tables]]
    [honeysql.core :as hsql]
    [clojure.set :refer [intersection]]
    [clojure.java.jdbc :as j]
    [clojure.tools.logging :refer [warn]]))

(defrecord HSQLView [id db-or-db-fn query-fn row-fn]
  IView
  (id [_] id)
  (data [_ namespace parameters]
    (let [db    (if (fn? db-or-db-fn)
                  (db-or-db-fn namespace)
                  db-or-db-fn)
          start (System/currentTimeMillis)
          data  (j/query db (hsql/format (apply query-fn parameters)) :row-fn row-fn)
          time  (- (System/currentTimeMillis) start)]
      (when (>= time 1000) (warn id "took" time "msecs"))
      data))
  (relevant? [_ namespace parameters hints]
    (let [tables (query-tables (apply query-fn parameters))
          nhints (filter #(and (= namespace (:namespace %))
                               (= hint-type (:type %))) hints)]
      (boolean (some #(not-empty (intersection (:hint %) tables)) nhints)))))

(defn view
  "Creates a Honey SQL view that uses a JDBC database configuration. The db passed in
   can either be a standard database connection map, or it can be a function that gets
   passed a namespace and returns a database connection map."
  [id db-or-db-fn hsql-fn & {:keys [row-fn]}]
  (HSQLView. id db-or-db-fn hsql-fn (or row-fn identity)))
