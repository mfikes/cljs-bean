# Overview

The `bean` function produces a thin wrapper over JavaScript objects, implementing the map abstraction:

```clojure
(require '[cljs-bean.core :refer [bean]])

(bean #js {:a 1, :b 2})
;; => {:a 1, :b 2}
```

This lets you interoperate with JavaScript objects in an idiomatic fashion, while being an order of 
magnitude faster than equivalent constructs using `js->clj`:

```clojure
(let [{:keys [a b]} (bean #js {:a 1, :b 2})]
  (+ a b))
```

If a bean is going to be retained, the object passed 
should be effectively immutable, as the resulting bean is backed by the object.

You can check if a map is implemented via `bean` using the `bean?` predicate, and you can extract the backing object from a bean using `object`.

The `bean` function behaves like Clojure’s in that it is not recursive:

```clojure
(bean #js {:a 1, :obj #js {:x 13, :y 17}})
;; => {:a 1, :obj #js {:x 13, :y 17}}
```
