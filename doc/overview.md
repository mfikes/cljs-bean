# Overview

The `bean` function produces a thin wrapper over JavaScript objects, implementing the map abstraction:

```clojure
(require '[cljs-bean.core :refer [bean]])

(bean #js {:a 1, :b 2})
;; {:a 1, :b 2}
```

If a bean is going to be retained, the object passed 
should be effectively immutable, as the resulting bean is backed by the object.

By default, the `bean` function behaves like Clojure’s in that it is not recursive:

```clojure
(bean #js {:a 1, :obj #js {:x 13, :y 17}, :arr #js [1 2 3]})
;; {:a 1, :obj #js {:x 13, :y 17}, :arr #js [1 2 3]}
```

On the other hand, CLJS Bean provides `->clj` and `->js` converters, which _are_ recursive.

```clojure
(require '[cljs-bean.core :refer [->clj ->js]])

(->clj #js {:a 1, :obj #js {:x 13, :y 17}, :arr #js [1 2 3]})
;; {:a 1, :obj {:x 13, :y 17}, :arr [1 2 3]}
```

You can update an object produced by `->clj` 

```clojure
(-> *1 (update-in [:obj :y] inc) (update :arr pop))
;; {:a 1, :obj {:x 13, :y 18}, :arr [1 2]}
```

and the result above can be converted back to JavaScript via a constant time call to `->js`:

```clojure
(->js *1)
;; #js {:a 1, :obj #js {:x 13, :y 18}, :arr #js [1 2]}
```
