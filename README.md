# CLJS Bean

Like `clojure.core/bean`, but for ClojureScript.

[![Clojars Project](https://img.shields.io/clojars/v/cljs-bean.svg)](https://clojars.org/cljs-bean) [![Build Status](https://travis-ci.org/mfikes/cljs-bean.svg?branch=master)](https://travis-ci.org/mfikes/cljs-bean)

The `bean` function produces a thin wrapper over JavaScript objects, implementing the map abstraction:

```clojure
(require '[cljs-bean.core :refer [bean]])

(bean #js {:a 1, :b 2})
;; => {:a 1, :b 2}
```

This lets you interoperate with JavaScript objects in an idiomatic fashion, while being an order of magnitude faster than equivalent constructs using `js->clj`:

```clojure
(let [{:keys [a b]} (bean obj)]
  (+ a b))
```

If the map produced by `bean` is going to be retained, the object passed 
should be effectively immutable, as the resulting map is backed by the object.

The map produced by `bean` employs keys matching the behavior of
ClojureScript printing of JavaScript objects. Object property names
are represented as simple keywords when possible, and as strings otherwise:

```clojure
(bean #js {:a 1, :b 2, "c/d" 3, "e f" 4})
;; => {:a 1, :b 2, "c/d" 3, "e f" 4}
```

The `bean` function behaves like Clojure’s in that it is not recursive:

```clojure
(bean #js {:a 1, :obj #js {:x 13, :y 17}})
;; => {:a 1, :obj #js {:x 13, :y 17}}
```
