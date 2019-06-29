# Object Extraction

Where possible, operations such as `assoc` and `conj` on a bean produce a new bean. 

In these cases, the `bean?` predicate will be satisfied on the result. If so, `object` 
can be used to extract the wrapped JavaScript object from the bean:

```clojure
(require '[cljs-bean.core :refer [bean bean? object]])

(assoc (bean #js {:a 1}) :b 2)
;; => {:a 1, :b 2}

(bean? *1)
;; => true

(object *2)
;; => #js {:a 1, :b 2}
```

This enables flexible and efficient ways to create JavaScript objects
using Clojure idioms, without having to reach for `clj->js`.

For example, the following builds a JavaScript object, setting its property values:

```clojure
(let [m {:a 1, :b 2, :c 3, :d 4, :e 5, :f 6, :g 7, :h 8}]
  (object (into (bean) (filter (comp odd? val)) m)))
;; => #js {:a 1, :c 3, :e 5, :g 7}
```

The example above is particularly efficient because no intermediate sequence is 
generated and—owing to transients support in beans—the properties are set by 
mutating a single object instance.

It is not possible for `assoc` or `conj` to produce a bean if, for example, a string key is
added to a bean configured to keywordize keys:

```clojure
(assoc (bean #js {:a 1}) "b" 2 :c 3)
;; => {:a 1, "b" 2, :c 3}

(bean? *1)
;; => false
```

The `->js` converter will automatically check and employ the fast-path 
constant time conversion where possible, falling back to `clj->js` if not.

Since `->clj` and `->js` are recursive, they can be used as simplified 
drop-in replacements for `js->clj` and `clj->js`, taking the fast path 
where possible.

In the following example, a thin wrapper produced by `->clj` allows the
use of `update-in` to produce a new JavaScript object, which is accessed
via `->js`:

```clojure
(require '[cljs-bean.core :refer [->clj ->js]])

(let [o #js {:a #js {:b 1}}]
  (-> o ->clj (update-in [:a :b] inc) ->js))
 ;; #js {:a #js {:b 2}}
```