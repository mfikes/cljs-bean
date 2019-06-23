# Recursive Beans

By default, the `bean` function behaves like Clojure’s in that it is not recursive:

```clojure
(bean #js {:a 1, :obj #js {:x 13, :y 17} :vec #js [1 2 3]})
;; => {:a 1, :obj #js {:x 13, :y 17} :vec #js [1 2 3]}
```

Beans can be made to behave more like `js->clj` by supplying `:recursive` `true`:

```clojure
(bean #js {:a 1, :obj #js {:x 13, :y 17} :vec #js [1 2 3]} :recursive true)
;; => {:a 1, :obj {:x 13, :y 17} :vec [1 2 3]}
```
