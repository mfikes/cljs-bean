(ns cljs-bean.transit
  (:require
    [cljs-bean.core]))

(defn- make-handlers []
  (when (exists? cognitect.transit/->MapHandler)
    (let [map-handler    (^:cljs.analyzer/no-resolve cognitect.transit/->MapHandler)
          list-handler   (^:cljs.analyzer/no-resolve cognitect.transit/->ListHandler)
          vector-handler (^:cljs.analyzer/no-resolve cognitect.transit/->VectorHandler)]
      {@#'cljs-bean.core/Bean           map-handler
       @#'cljs-bean.core/BeanSeq        list-handler
       @#'cljs-bean.core/ArrayVector    vector-handler
       @#'cljs-bean.core/ArrayVectorSeq list-handler})))

(let [handlers (volatile! nil)]
  (defn- get-handlers []
    (if-some [h @handlers]
      h
      (vreset! handlers (make-handlers)))))

(defn writer-handlers
  "Returns a map of handlers for use with cognitect.transit/writer which
  enables marshalling CLJS Bean types to Transit data. If cognitect.transit has
  not been required, returns nil."
  []
  (get-handlers))
