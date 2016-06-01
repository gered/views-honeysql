(ns views.honeysql.test-fixtures
  (:use
    clojure.test
    views.honeysql.core))

(def test-db {:not-a-real-db-conn true})

(def test-view-system (atom {}))

(defn reset-test-view-system-fixture [f]
  (reset! test-view-system {})
  (f))

(def redefs-called (atom {}))

(defn ->redef-fn
  [name & [return-value]]
  (fn [& args]
    (swap! redefs-called update-in [name]
           #((fnil conj []) % (vec args)))
    return-value))

(defn called-with-args?
  [fn-name-kw & args]
  (->> (get @redefs-called fn-name-kw)
       (some #(= % (vec args)))
       (boolean)))

(defn not-called?
  [fn-name-kw]
  (not (contains? @redefs-called fn-name-kw)))
