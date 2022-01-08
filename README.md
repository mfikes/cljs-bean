# CLJS Bean

Like `clojure.core/bean`, but for ClojureScript.

[![Clojars Project](https://img.shields.io/clojars/v/cljs-bean.svg)](https://clojars.org/cljs-bean) [![cljdoc badge](https://cljdoc.org/badge/cljs-bean/cljs-bean)](https://cljdoc.org/d/cljs-bean/cljs-bean/CURRENT) [![Build Status](https://travis-ci.org/mfikes/cljs-bean.svg?branch=master)](https://travis-ci.org/mfikes/cljs-bean)

A `bean` function and `->clj` and `->js` converters enable working with JavaScript objects via thin wrappers implementing ClojureScript collection abstractions:

```clojure
(require '[cljs-bean.core :refer [bean ->clj ->js]])

(bean #js {:a 1, :b 2})
;; {:a 1, :b 2}
```

The converters enable idiomatic interop while being much faster than 
equivalent constructs using `js->clj` and `clj->js`:

```clojure
(let [{:keys [a b]} (->clj #js {:a 1, :b 2})]
  (+ a b))
;; => 3
  
(into (->clj #js [1 2]) [3 4 5])
;; [1 2 3 4 5]

(->js *1)
;; #js [1 2 3 4 5]
```

Read more:

[Overview](doc/overview.md)

[Object Extraction](doc/object.md)

[Recursive Beans](doc/recursive.md)

[Key Mapping](doc/key-mapping.md)

[Transit](doc/transit.md)

## License

Copyright © 2019–2022 Mike Fikes

Distributed under the EPL License, same as Clojure. See LICENSE.

The namespace `cljs-bean.from.cljs.core` contains source from [`ClojureScript`](https://github.com/clojure/clojurescript) which is licensed under the EPL license.
