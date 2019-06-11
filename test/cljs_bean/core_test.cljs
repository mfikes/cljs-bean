(ns cljs-bean.core-test
  (:require
   [clojure.test :refer [are deftest is]]
   [cljs-bean.core :refer [bean]]))

(def color-black
  #js {:red          0
       :blue         0
       :green        0
       :RGB          -16777216
       :alpha        255
       :transparency 1})

(deftest test-bean
  (let [b (bean color-black)]
    (are [x y] (= x y)
      (map? b) true

      (:red b) 0
      (:green b) 0
      (:blue b) 0
      (:RGB b) -16777216

      (:alpha b) 255
      (:transparency b) 1

      (:missing b) nil
      (:missing b :default) :default
      (get b :missing) nil
      (get b "red") nil
      (get b :missing :default) :default)))

(deftest qualified-name-lookup
  (let [b (bean #js {"my-ns/my-name" 17})]
    (is (= 17 (get b "my-ns/my-name")))))

(deftest dot-toString-test
  (is (= "{:a 1}" (.toString (bean #js {:a 1}))))
  (is (= "{\"a/b\" 1}" (.toString (bean #js {"a/b" 1})))))

(deftest dot-equiv-test
  (is (.equiv (bean #js {:a 1}) {:a 1}))
  (is (.equiv (bean #js {"a/b" 1}) {"a/b" 1})))

(deftest keys-test
  (is (= [:a] (keys (bean #js {:a 1}))))
  (is (= ["a/b"] (keys (bean #js {"a/b" 1})))))

(deftest vals-test
  (is (= [1] (vals (bean #js {:a 1})))))

(deftest clone-test
  (let [o (bean #js {:a 1})
        c (clone o)]
    (is (= o c))
    (is (not (identical? o c)))
    (is (nil? (meta c))))
  (let [o (with-meta (bean #js {:a 1}) {:foo true})
        c (clone o)]
    (is (= o c))
    (is (not (identical? o c)))
    (is (= {:foo true} (meta c)))))

(deftest meta-test
  (let [o (bean #js {:a 1})]
    (= {:foo true} (meta (with-meta o {:foo true})))))

(deftest conj-test
  (let [b (bean #js {:x 1})
        m (conj b [:y 2])]
    (is (map? m))
    (is (= m {:x 1 :y 2}))))

(deftest empty-test
  (is (= {} (empty (bean #js {:a 1}))))
  (let [m (with-meta (bean #js {:a 1}) {:foo true})]
    (= {:foo true} (meta (empty m)))))

(deftest equiv-test
  (is (-equiv (bean #js {:a 1}) {:a 1}))
  (is (-equiv (bean #js {"a/b" 1}) {"a/b" 1})))

(deftest hash-test
  (is (== (hash {:a 1}) (hash (bean #js {:a 1})))))

(deftest iterator-test
  (let [i (-iterator (bean #js {:a 1}))]
    (is (true? (.hasNext i)))
    (is (= [:a 1] (.next i)))))

(deftest seq-test
  (is (nil? (seq (bean #js {}))))
  (is (= [[:a 1]] (seq (bean #js {:a 1}))))
  (is (= [:a] (keys (seq (bean #js {:a 1})))))
  (is (= [1] (vals (seq (bean #js {:a 1}))))))

(deftest assoc-test
  (let [b (bean #js {:x 1})
        m (assoc b :y 2)]
    (is (map? m))
    (is (= m {:x 1 :y 2}))))

(deftest contains?-test
  (let [b (bean color-black)]
    (is (contains? b :red))
    (is (not (contains? b "red")))
    (is (not (contains? b :missing)))))

(deftest find-test
  (let [b (bean color-black)]
    (is (map-entry? (find b :red)))
    (is (= [:red 0] (find b :red))))
  (let [b (bean #js {"my-ns/my-name" 17})]
    (is (= ["my-ns/my-name" 17] (find b "my-ns/my-name")))))

(deftest dissoc-test
  (let [b (bean #js {:a 1, :b 2})]
    (is (= {:a 1} (dissoc b :b)))))

(deftest reduce-kv-test
  (is (= {1 :a, 2 :b, 3 :c}
        (reduce-kv #(assoc %1 %3 %2) {} (bean #js {:a 1 :b 2 :c 3}))))
  (is (= 1 (reduce-kv (fn [r k v] (reduced v))
             nil
             (bean #js {:a 1})))))

(deftest reduce-test
  (is (= [:a 1 :b 2 :c 3]
        (reduce into (bean #js {:a 1, :b 2, :c 3}))))
  (is (= {:a 2, :b 3}
        (reduce (fn [r [k v]]
                  (assoc r k (inc v)))
          {}
          (bean #js {:a 1, :b 2})))))

(deftest ifn-test
  (let [b (bean color-black)]
    (is (= -16777216 (b :RGB))))
  (let [b (bean color-black)]
    (is (= ::not-found (b :bogus ::not-found)))))

(deftest editable-collection-test
  (is (= {:a 1, :b 2} (persistent! (assoc! (transient (bean #js {:a 1})) :b 2)))))
