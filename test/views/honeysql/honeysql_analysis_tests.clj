(ns views.honeysql.honeysql-analysis-tests
  (:use
    clojure.test
    views.honeysql.test-fixtures
    views.honeysql.util)
  (:require
    [honeysql.core :as hsql]))

(deftest simple-from-clause
  (is (= #{:foo}
         (query-tables
           {:select [:a :b :c]
            :from   [:foo]}))))

(deftest from-clause-with-multiple-tables
  (is (= #{:foo :bar}
         (query-tables
           {:select [:foo.a :foo.b :foo.c :bar.d]
            :from   [:foo :bar]}))))

(deftest inner-join
  (is (= #{:foo :bar}
         (query-tables
           {:select [:a :b :c]
            :from   [:foo]
            :join   [:bar [:= :d :a]]}))))

(deftest left-join
  (is (= #{:foo :bar}
         (query-tables
           {:select    [:a :b :c]
            :from      [:foo]
            :left-join [:bar [:= :d :a]]}))))

(deftest right-join
  (is (= #{:foo :bar}
         (query-tables
           {:select     [:a :b :c]
            :from       [:foo]
            :right-join [:bar [:= :d :a]]}))))

(deftest table-names-with-aliases
  (is (= #{:foo :bar}
         (query-tables
           {:select [:a :b :c]
            :from   [[:foo :f]]
            :join   [[:bar :b] [:= :b.foo_id :f.foo_id]]}))))

(deftest where-clause-subquery
  (is (= #{:foo :bar}
         (query-tables
           {:select [:*]
            :from   [[:foo :f]]
            :where  [:in :f.foo_id {:select [:b.foo_id]
                                    :from   [[:bar :b]]}]}))))

(deftest insert-query
  (is (= #{:foo}
         (query-tables
           {:insert-into :foo
            :values      [{:a "a" :b "b" :c "c"}]}))))

(deftest update-query
  (is (= #{:foo}
         (query-tables
           {:update :foo
            :set    {:a "aaa" :b "bbb" :c "ccc"}
            :where  [:= :foo_id 42]}))))

(deftest delete-query
  (is (= #{:foo}
         (query-tables
           {:delete-from :foo
            :where       [:= :foo_id 42]}))))

(deftest with-clause
  ; this query shamelessly taken from https://www.postgresql.org/docs/8.4/static/queries-with.html
  ; and converted to honeysql for this test
  ; NOTE: tables returned include the names of the CTE temp tables which is
  ;       probably not really desired behaviour. should fix this.
  ;       however, for now this test does demonstrate it is correctly looking at sub-queries
  ;       inside of the with-clause.
  (is (= #{:orders :top_regions :regional_sales}
         (query-tables
           {:with     [[:regional_sales
                        {:select   [:region [(hsql/call :sum :amount) "total_sales"]]
                         :from     [:orders]
                         :group-by [:region]}]
                       [:top_regions
                        {:select [:region]
                         :from   [:regional_sales]
                         :where  [:> :total_sales {:select [(hsql/raw "SUM(total_sales) / 10")]
                                                   :from   [:regional_sales]}]}]]
            :select   [:region :product [(hsql/call :sum :quantity) "product_units"] [(hsql/call :sum :amount) "product_sales"]]
            :from     [:orders]
            :where    [:in :region {:select [:region]
                                    :from   [:top_regions]}]
            :group-by [:region :product]}))))
