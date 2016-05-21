(ns views.honeysql.core
  (:require
    [views.core :refer [view-system hint put-hints!]]
    [views.honeysql.util :refer [query-tables]]
    [honeysql.core :as hsql]
    [clojure.tools.logging :refer [error]]
    [clojure.java.jdbc :as j]))

(def hint-type :sql-table-name)

(defmacro with-view-transaction
  "Like jdbc's with-db-transaction, but sends view hints at end of transaction."
  [binding & forms]
  (let [tvar (first binding), db (second binding), args (drop 2 binding)]
    `(if (:hints ~db) ;; check if we are in a nested transaction
       (let [~tvar ~db] ~@forms)
       (let [hints#   (atom [])
             result#  (j/with-db-transaction [t# ~db ~@args]
                                             (let [~tvar (assoc ~db :hints hints#)]
                                               ~@forms))]
         (put-hints! @hints#)
         result#))))

(defn- execute-honeysql!
  "Always return keys for inserts."
  [db hsql-map]
  (if-let [table (:insert-into hsql-map)]
    (if (vector? table)
      (j/execute! db (hsql/format hsql-map))
      (apply j/insert! db table (:values hsql-map)))
    (j/execute! db (hsql/format hsql-map))))

(defn vexec!
  "Used to perform arbitrary insert/update/delete actions on the database,
   while ensuring that view hints are sent to the view system.
   Arguments are:
   - db: a clojure.java.jdbc database connection
   - namespace (optional): a namespace that will be included in the hints sent out
   - action-map: the HoneySQL map for the insert/update/delete action"
  ([db namespace action-map]
   (let [results   (execute-honeysql! db action-map)
         hsql-hint (hint namespace (query-tables action-map) hint-type)]
     (if-let [hints (:hints db)]
       (swap! hints conj hsql-hint)
       (put-hints! [hsql-hint]))
     results))
  ([db action-map]
    (vexec! db nil action-map)))
