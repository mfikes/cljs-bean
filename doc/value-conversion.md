# Value Conversion

If you need to explicitly control how values are transformed when converting from JavaScript to ClojureScript, you can pass a `:->val` option to `bean`. In this case, any values are passed to your function instead of applying the default logic.

For example, let's say that there's a specific JavaScript object type that needs special handling. This can be accomplished with code like the following:

```clojure
(require '[cljs-bean.core :refer [bean ->clj]]
         '[goog.object :as gobj])

(defn ->val [x]
  (if (and (object? x) (gobj/containsKey x "epoch"))
    (js/Date. (gobj/get x "epoch"))
    (->clj x)))

(bean #js {:start #js {:epoch 1567988289000}
           :pos #js {:x 102, :y 179}}
  :->val ->val)
;; {:start #inst "2019-09-09T00:18:09.000-00:00"
;;  :pos {:x 102, :y 179}}
```
