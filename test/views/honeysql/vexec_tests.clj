(ns views.honeysql.vexec-tests
  (:use
    clojure.test
    views.honeysql.test-fixtures
    views.honeysql.core)
  (:require
    [clojure.java.jdbc :as jdbc]
    [views.core :as views]
    [honeysql.core :as hsql]))

(defn vexec-redefs-fixture [f]
  (reset! redefs-called {})
  (with-redefs
    [jdbc/insert!     (->redef-fn :jdbc/insert! :jdbc/insert!-return-value)
     jdbc/execute!    (->redef-fn :jdbc/execute! :jdbc/execute!-return-value)
     views/put-hints! (->redef-fn :views/put-hints!)]
    (f)))


(use-fixtures :each reset-test-view-system-fixture vexec-redefs-fixture)

(deftest vexec-runs-query-and-puts-hints
  (let [sql    {:insert-into :example
                :values [{:field1 "test" :field2 "N" :field3 nil}]}
        result (vexec! test-view-system test-db sql)]
    (is (called-with-args? :jdbc/insert! test-db (:insert-into sql) (first (:values sql))))
    (is (= :jdbc/insert!-return-value result))
    (is (called-with-args? :views/put-hints! test-view-system [(views/hint nil #{:example} hint-type)]))))

(deftest namespace-is-passed-along-to-hints-via-vexec
  (let [sql    {:insert-into :example
                :values [{:field1 "test" :field2 "N" :field3 nil}]}
        result (vexec! test-view-system test-db :foobar sql)]
    (is (called-with-args? :jdbc/insert! test-db (:insert-into sql) (first (:values sql))))
    (is (= :jdbc/insert!-return-value result))
    (is (called-with-args? :views/put-hints! test-view-system [(views/hint :foobar #{:example} hint-type)]))))

(deftest vexec-runs-update-queries
  (let [sql    {:update :example
                :set {:field1 "foo" :field2 "bar" :field3 "baz"}
                :where [:= :id 42]}
        result (vexec! test-view-system test-db sql)]
    (is (called-with-args? :jdbc/execute! test-db (hsql/format sql)))
    (is (= :jdbc/execute!-return-value result))
    (is (called-with-args? :views/put-hints! test-view-system [(views/hint nil #{:example} hint-type)]))))

(deftest vexec-runs-delete-queries
  (let [sql    {:delete-from :example
                :where [:= :id 42]}
        result (vexec! test-view-system test-db sql)]
    (is (called-with-args? :jdbc/execute! test-db (hsql/format sql)))
    (is (= :jdbc/execute!-return-value result))
    (is (called-with-args? :views/put-hints! test-view-system [(views/hint nil #{:example} hint-type)]))))
