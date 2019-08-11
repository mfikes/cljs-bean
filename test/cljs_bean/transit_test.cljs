(ns cljs-bean.transit-test
  (:require
    [cljs-bean.core :refer [->clj]]
    [cljs-bean.transit]
    [clojure.test :refer [deftest is]]
    [cognitect.transit :as t]))

(defn roundtrip [x]
  (let [w (t/writer :json
            {:handlers (cljs-bean.transit/writer-handlers)})
        r (t/reader :json)]
    (t/read r (t/write w x))))

(deftest issue-70-test
  (is (= {:a 1} (roundtrip (->clj #js {:a 1}))))
  (is (= [1 2] (roundtrip (->clj #js [1 2 ]))))
  (is (= [[:a 1]] (roundtrip (seq (->clj #js {:a 1})))))
  (is (= [2] (roundtrip (rest (->clj #js [1 2])))))
  (is (= {:a 1, :b [1 2], :c [2 3]}
        (roundtrip (-> (->clj #js {:a 1 :b #js [1 2] :c #js [1 2 3]})
                     (update :c rest))))))
