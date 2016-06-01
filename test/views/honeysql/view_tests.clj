(ns views.honeysql.view-tests
  (:use
    clojure.test
    views.honeysql.test-fixtures
    views.honeysql.core
    views.honeysql.view
    views.protocols)
  (:require
    [clojure.java.jdbc :as jdbc]
    [views.core :as views]
    [honeysql.core :as hsql]))

(defn view-redefs-fixture [f]
  (reset! redefs-called {})
  (with-redefs
    [jdbc/query       (->redef-fn :jdbc/query :jdbc/query-return-value)
     views/put-hints! (->redef-fn :views/put-hints!)]
    (f)))


(use-fixtures :each reset-test-view-system-fixture view-redefs-fixture)

(deftest basic-sql-view-works
  (let [sql-fn   (fn [] {:select [:*]
                         :from   [:foobar]})
        sql-view (view :test-view test-db sql-fn)]
    (is (satisfies? IView sql-view))
    (is (= :test-view (id sql-view)))
    (is (= true (relevant? sql-view nil [] [(views/hint nil #{:foobar} hint-type)])))
    (is (= false (relevant? sql-view nil [] [(views/hint nil #{:baz} hint-type)])))
    (is (= :jdbc/query-return-value (data sql-view nil [])))
    (is (called-with-args? :jdbc/query test-db (hsql/format (sql-fn)) {}))))

(deftest basic-sql-view-works-with-parameters
  (let [sql-fn   (fn [a b]
                   {:select [:*]
                    :from   [:foobar]
                    :where  [:and
                             [:= :a a]
                             [:= :b b]]})
        sql-view (view :test-view test-db sql-fn)]
    (is (= true (relevant? sql-view nil [1 2] [(views/hint nil #{:foobar} hint-type)])))
    (is (= :jdbc/query-return-value (data sql-view nil [1 2])))
    (is (called-with-args? :jdbc/query test-db (hsql/format (sql-fn 1 2)) {}))))

(deftest basic-sql-view-works-with-namespace
  (let [sql-fn   (fn [] {:select [:*]
                         :from   [:foobar]})
        sql-view (view :test-view test-db sql-fn)]
    (is (= true (relevant? sql-view :abc [] [(views/hint :abc #{:foobar} hint-type)])))
    (is (= false (relevant? sql-view :123 [] [(views/hint :abc #{:foobar} hint-type)])))
    (is (= :jdbc/query-return-value (data sql-view nil [])))
    (is (called-with-args? :jdbc/query test-db (hsql/format (sql-fn)) {}))))

(deftest view-db-fn-is-used-when-provided
  (let [alt-test-db {:alternate-test-db-conn true}
        db-fn       (fn [namespace] alt-test-db)
        sql-fn      (fn [] {:select [:*]
                            :from   [:foobar]})
        sql-view    (view :test-view db-fn sql-fn)]
    (is (= :jdbc/query-return-value (data sql-view nil [])))
    (is (called-with-args? :jdbc/query alt-test-db (hsql/format (sql-fn)) {}))))

(deftest view-db-fn-is-passed-namespace
  (let [test-namespace :test-namespace
        alt-test-db    {:alternate-test-db-conn true}
        db-fn          (fn [namespace]
                         (is (= namespace :test-namespace))
                         alt-test-db)
        sql-fn         (fn [] {:select [:*]
                               :from   [:foobar]})
        sql-view       (view :test-view db-fn sql-fn)]
    (is (= :jdbc/query-return-value (data sql-view test-namespace [])))
    (is (called-with-args? :jdbc/query alt-test-db (hsql/format (sql-fn)) {}))))

(deftest row-and-result-set-fns-are-passed-to-jdbc
  (let [row-fn        (fn [row] row)
        result-set-fn (fn [results] results)
        sql-fn        (fn [] {:select [:*]
                              :from   [:foobar]})
        sql-view      (view :test-view test-db sql-fn {:row-fn        row-fn
                                                       :result-set-fn result-set-fn})]
    (is (= :jdbc/query-return-value (data sql-view nil [])))
    (is (called-with-args? :jdbc/query test-db (hsql/format (sql-fn)) {:row-fn        row-fn
                                                                       :result-set-fn result-set-fn}))))
