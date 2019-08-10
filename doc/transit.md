# Transit

When marshalling data containing CLJS Bean types to Transit, special handlers must be 
specified when creating a Transit writer.

The CLJS Bean custom handlers are provided by calling `cljs-bean.core/transit-writer-handlers`, 
and are passed to Transit's `writer` constructor via the `:handlers` option. For example:

```clojure
(require '[cognitect.transit :as t] 
         '[cljs-bean.core :refer [->clj]])

(defn roundtrip [x]
  (let [w (t/writer :json 
           {:handlers (cljs-bean.core/transit-writer-handlers)})
        r (t/reader :json)]
    (t/read r (t/write w x))))
```

With this set up, Transit marshalling will work properly.

For example `(roundtrip (->clj #js {:a 1}))` will produce the map `{:a 1}`.

Note that, on the receiving end, plain ClojureScript (not CLJS Bean) types are produced.

The custom handlers cover all of the CLJS Bean types. Examples:

- `(->clj #js {:a 1})` is a bean, which becomes a persistent map when round tripping
- `(->clj #js [1 2])` is a CLJS Bean vector type, which becomes a persistent vector when round tripping
- `(seq (->clj #js {:a 1}))` is a CLJS Bean sequence type, which which becomes a persistent list when round tripping
- `(rest (->clj #js [1 2]))` is a CLJS Bean sequence type, which which becomes a persistent list when round tripping

With these handlers, nested types are properly supported. For example

```
(-> (->clj #js {:a 1 :b #js [1 2] :c #js [1 2 3]})
 (update :c rest))
```

produces a bean, where the values under `:b` and `:c` are types specific to CLJS Bean

```
{:a 1, :b [1 2], :c (2 3)}
```

and if this value is passed to `roundtrip`, the result is a persistent map containing a 
persistent vector and persistent list.