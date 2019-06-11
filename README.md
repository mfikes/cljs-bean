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
(let [{:keys [a b]} (bean #js {:a 1, :b 2})]
  (+ a b))
```

If the map produced by `bean` is going to be retained, the object passed 
should be effectively immutable, as the resulting map is backed by the object.

The `bean` function behaves like Clojure’s in that it is not recursive:

```clojure
(bean #js {:a 1, :obj #js {:x 13, :y 17}})
;; => {:a 1, :obj #js {:x 13, :y 17}}
```

## Key Mapping

The map produced by `bean` keywordizes the keys. If instead you pass `:keywordize-keys` `false`,
string keys will be produced:

```clojure
(bean #js {:a 1, :b 2, "c/d" 3, "e f" 4} :keywordize-keys false)
;; => {"a" 1, "b" 2, "c/d" 3, "e f" 4}
```

You can control key to property name management by supplying both `:key->prop` and `:prop->key`.
For example, to mimic the behavior of ClojureScript's JavaScript object literal syntax, where
keywords are used only if they can be represented as simple keywords:

```clojure
(defn prop->key [prop]
  (cond-> prop
    (some? (re-matches #"[A-Za-z_\*\+\?!\-'][\w\*\+\?!\-']*" prop)) keyword))

(defn key->prop [key]
  (cond
    (simple-keyword? key) (name key)
    (and (string? key) (string? (prop->key key))) key
    :else nil))

(bean #js {:a 1, :b 2, "c/d" 3, "e f" 4} :prop->key prop->key :key->prop :key->prop)
;; => {:a 1, :b 2, "c/d" 3, "e f" 4}
```
