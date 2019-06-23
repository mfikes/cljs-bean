# Key Mapping

By default, the map produced by `bean` keywordizes the keys. If instead you pass `:keywordize-keys` `false`,
string keys will be produced:

```clojure
(bean #js {:a 1, :b 2, "c/d" 3, "e f" 4} :keywordize-keys false)
;; => {"a" 1, "b" 2, "c/d" 3, "e f" 4}
```

In either of these modes, `bean` is meant to interoperate with JavaScript objects 
via property names that will not be renamed by Google Closure Compiler.

You can control the key to property name mapping by supplying both `:key->prop` and `:prop->key`.

The following example mimics the behavior of ClojureScript's JavaScript object literal syntax, where
keywords are used only if properties can be represented as simple keywords:

```clojure
(defn prop->key [prop]
  (cond-> prop
    (some? (re-matches #"[A-Za-z_\*\+\?!\-'][\w\*\+\?!\-']*" prop)) keyword))

(defn key->prop [key]
  (cond
    (simple-keyword? key) (name key)
    (and (string? key) (string? (prop->key key))) key
    :else nil))

(bean #js {:a 1, :b 2, "c/d" 3, "e f" 4} :prop->key prop->key :key->prop key->prop)
;; => {:a 1, :b 2, "c/d" 3, "e f" 4}
```
