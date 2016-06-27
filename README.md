# views.honeysql

[HoneySQL][1] plugin for the [views][2] library. Allows for views to be 
created which retrieve data via [clojure.java.jdbc][3] using SQL 
queries provided as Clojure maps. Provides an alternate `execute!`-like
function to execute INSERT/UPDATE/DELETE queries and add appropriate 
hints to the view system at the same time to trigger view refreshes.

[1]: https://github.com/jkk/honeysql
[2]: https://github.com/gered/views
[3]: https://github.com/clojure/java.jdbc

views.honeysql interops well with [views.sql][4] when both types of 
views are included within the same system.

[4]: https://github.com/gered/views.sql

## Leiningen

```clj
[gered/views.honeysql "0.2"]
```

### This is a fork!
This is a fork of the [original][5] by Kira Inc. I made some tweaks
to keep things consistent with the changes in my fork of the views
library, but not much else has been changed. Since I'm keeping my fork
of the views library separate for now this library will also be kept 
separate along with it.

[5]: https://github.com/kirasystems/views-honeysql

You will **not** be able to use this fork of views.honeysql 
successfully with the original views library!


## Creating SQL Views

```clj
(require '[views.core :as views]
         '[views.honeysql.view :as vhsql])

(def db ... )            ; a standard JDBC database connection map
(def view-system ... )   ; pre-initialized view system


; view functions. these are just functions that return HoneySQL maps.
; (you could also use honeysql.core/build to build the HoneySQL maps if you wish)

(defn my-view-sql []
  {:select [:*]
   :from [:foo]})

(defn people-by-type-sql [type]
  {:select [:first_name :last_name]
   :from [:people]
   :where [:= :type type]})


; add 2 views, :my-view and :people-by-type, to the view system

(views/add-views!
  view-system
  [(vhsql/view :my-view db my-view-sql)
   (vhsql/view :people-by-type db people-by-type-sql)])
```

The calls to `views.honeysql.view/view` return instances of a 
`HSQLView` view. The "view functions" which contain the actual HoneySQL 
queries are called in two instances:

* When the view's data is being refreshed. The view function is called
to get the SQL to be run via `clojure.java.jdbc/query` using the `db`
connection that was provided to the view.
* Whenever hints are being checked for relevancy against the view when
the view system is determining whether the view needs to be refreshed
or not.

Note also that the view functions can take any number of parameters
which are provided during view subscription:

```clj
(require '[views.core :refer [subscribe! ->view-sig]])

(subscribe! view-system (->view-sig :my-namespace :my-view []) 123 nil)
(subscribe! view-system (->view-sig :my-namespace :people-by-type ["student"]) 123 nil)
```

### Extra Features and Options

You can use clojure.java.jdbc's `:row-fn` and `:result-set-fn` (see
[here][6] and [here][7] for more info on what these options are) with
HoneySQL views:

```clj
(vhsql/view :foobar-view db foobar-view-sql {:row-fn my-row-fn 
                                             :result-set-fn my-result-set-fn})
```

[6]: http://clojure-doc.org/articles/ecosystem/java_jdbc/using_sql.html#processing-each-row-lazily
[7]: http://clojure-doc.org/articles/ecosystem/java_jdbc/using_sql.html#processing-a-result-set-lazily

Additionally the `db` argument can be a function that accepts a
namespace and returns a standard database connection map.

```clj
(defn db-selector [namespace]
  (case namespace
    :foo foo-db
    :bar bar-db
    default-db))

(vhsql/view :people-by-type db-selector people-by-type-sql)
```

In this case, `db-selector` would be called only when the view data is
being refreshed (it is not used during hint relevancy checks). The
namespace that would be passed in is taken from the view
subscription(s) for which the view is being refresh for (so it could
be anything, even `nil`... whatever was provided as the namespace at
the time subscriptions are created).


## Running INSERT/UPDATE/DELETE Queries

Instead of using clojure.java.jdbc's `execute!` or `query!`, you
should instead use `views.honeysql.core/vexec!`:

```clj
(require '[views.honeysql.core :refer [vexec!]])

(vexec! view-system db
        {:insert-into :people
         :values [{:type "student"
                   :first_name "Foo"
                   :last_name "Bar"}]})
```

This will both, execute the SQL query and also analyze it to determine
what hints need to be added to the view system and then add them.

With the above `vexec!` call the hints that would be added to the view
system would trigger view refreshes for anyone subscribed to any 
HoneySQL views in the system that use a SELECT query to retrieve data 
from the "people" table (either using another simple SELECT, or JOINing
it with other tables as part of a larger query, a sub-SELECT, etc).

### Transactions

If you need to run some SQL queries within a transaction, you should
use `views.honeysql.core/with-view-transaction` instead of 
clojure.java.jdbc's `with-db-transaction`. It basically works exactly
the same:

```clj
(require '[views.honeysql.core :refer [with-view-transaction]])

(with-view-transaction
  view-system            ; need to pass in the view-system atom
  [dt db]
  (vexec! view-system dt
          {:insert-into :users
           :values [{:username "fbar"}]})
  (vexec! view-system dt
          {:insert-into :people
           :values [{:type "student"
                     :first_name "Foo"
                     :last_name "Bar"
                     :user_id {:select [:u.user_id]
                               :from [[:users :u]]
                               :where [:= :u.username "fbar"]}}]}))
```

The hints generated by any `vexec!` calls within a transaction are
collected in a list and only at the end of the (successful) transaction
are they added to the view system.

### Namespaces

Namespaces can be specified in an additional options map as the last
argument to `vexec!`. If you don't provide this, then a `nil` namespace
is used for the hints sent to the view system.

```clj
(vexec! view-system db
        {:insert-into :people
         :values [{:type "student"
                   :first_name "Foo"
                   :last_name "Bar"}]}
        {:namespace :my-namespace)
```


## Hints

Hints for the view system are automatically determined from the SQL
queries being used in the view functions and from `vexec!` calls by
analyzing the HoneySQL map and figuring out what tables are being 
queried from or changed. All you need to do is write the HoneySQL query.

The hints themselves are simply SQL table names represented as 
keywords, e.g. `:people` for the "people" table. Hints are considered
relevant to a HoneySQL view if the list of tables being queried from in
the view's SELECT statement have at least some matches against the
hints being compared against.

Since HoneySQL maps are easily parsed, this should "just work" as long
as you're writing correctly formatted HoneySQL. HoneySQL gives you
various ways to also make use of vendor-specific extensions should you
need them, and this shouldn't be a problem when it comes time to parsing
the HoneySQL map to get the hints from it.

> Hints generated by views.honeysql are compatible with the hints 
> generated by [views.sql][8], so you can easily mix-and-match these 
> views within the same system and get view refreshes triggered as you
> would expect for both types of views.

[8]: https://github.com/gered/views.sql


## License

Copyright © 2015-2016 Kira Inc.

Various updates in this fork by Gered King (https://github.com/gered)

Distributed under the MIT License.
