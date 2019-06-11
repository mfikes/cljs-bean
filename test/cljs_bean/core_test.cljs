(ns cljs-bean.core-test
  (:require
   [clojure.test :refer [are deftest is]]
   [cljs-bean.core :refer [bean]]))

(defn prop->key [prop]
  (cond-> prop
    (some? (re-matches #"[A-Za-z_\*\+\?!\-'][\w\*\+\?!\-']*" prop)) keyword))

(defn key->prop [key]
  (cond
    (simple-keyword? key) (name key)
    (and (string? key) (string? (prop->key key))) key
    :else nil))

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
    (is (= 17 (get b :my-ns/my-name))))
  (let [b (bean #js {"my-ns/my-name" 17} :keywordize-keys false)]
    (is (= 17 (get b "my-ns/my-name"))))
  (let [b (bean #js {"my-ns/my-name" 17} :prop->key prop->key :key->prop key->prop)]
    (is (= 17 (get b "my-ns/my-name")))))

(deftest dot-toString-test
  (is (= "{:a 1}" (.toString (bean #js {:a 1}))))
  (is (= "{\"a\" 1}" (.toString (bean #js {:a 1} :keywordize-keys false))))
  (is (= "{:a 1}" (.toString (bean #js {:a 1} :prop->key prop->key :key->prop key->prop))))
  (is (#{"#:a{:b 1}" "{:a/b 1}"} (.toString (bean #js {"a/b" 1}))))
  (is (= "{\"a/b\" 1}" (.toString (bean #js {"a/b" 1} :keywordize-keys false))))
  (is (= "{\"a/b\" 1}" (.toString (bean #js {"a/b" 1} :prop->key prop->key :key->prop key->prop)))))

(deftest dot-equiv-test
  (is (.equiv (bean #js {:a 1}) {:a 1}))
  (is (.equiv (bean #js {:a 1} :keywordize-keys false) {"a" 1}))
  (is (.equiv (bean #js {:a 1} :prop->key prop->key :key->prop key->prop) {:a 1}))
  (is (.equiv (bean #js {"a/b" 1}) {:a/b 1}))
  (is (.equiv (bean #js {"a/b" 1} :keywordize-keys false) {"a/b" 1}))
  (is (.equiv (bean #js {"a/b" 1} :prop->key prop->key :key->prop key->prop) {"a/b" 1})))

(deftest keys-test
  (is (= [:a] (keys (bean #js {:a 1}))))
  (is (= ["a"] (keys (bean #js {:a 1} :keywordize-keys false))))
  (is (= [:a] (keys (bean #js {:a 1} :prop->key prop->key :key->prop key->prop))))
  (is (= [:a/b] (keys (bean #js {"a/b" 1}))))
  (is (= ["a/b"] (keys (bean #js {"a/b" 1} :keywordize-keys false))))
  (is (= ["a/b"] (keys (bean #js {"a/b" 1} :prop->key prop->key :key->prop key->prop)))))

(deftest vals-test
  (is (= [1] (vals (bean #js {:a 1})))))

(deftest clone-test
  (let [o (bean #js {:a 1})
        c (clone o)]
    (is (= o c))
    (is (not (identical? o c)))
    (is (nil? (meta c))))
  (let [o (bean #js {:a 1} :keywordize-keys false)
        c (clone o)]
    (is (= o c))
    (is (not (identical? o c)))
    (is (nil? (meta c))))
  (let [o (bean #js {:a 1, "a/b" 2, "d e" 3} :prop->key prop->key :key->prop key->prop)
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
    (= {:foo true} (meta (with-meta o {:foo true}))))
  (let [o (bean #js {:a 1} :keywordize-keys false)]
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
  (is (-equiv (bean #js {:a 1} :keywordize-keys false) {"a" 1}))
  (is (-equiv (bean #js {:a 1} :prop->key prop->key :key->prop key->prop) {:a 1}))
  (is (-equiv (bean #js {"a/b" 1}) {:a/b 1}))
  (is (-equiv (bean #js {"a/b" 1} :keywordize-keys false) {"a/b" 1}))
  (is (-equiv (bean #js {"a/b" 1} :prop->key prop->key :key->prop key->prop) {"a/b" 1})))

(deftest hash-test
  (is (== (hash {:a 1}) (hash (bean #js {:a 1}))))
  (is (== (hash {"a" 1}) (hash (bean #js {:a 1} :keywordize-keys false)))))

(deftest iterator-test
  (let [i (-iterator (bean #js {:a 1}))]
    (is (true? (.hasNext i)))
    (is (= [:a 1] (.next i))))
  (let [i (-iterator (bean #js {:a 1} :keywordize-keys false))]
    (is (true? (.hasNext i)))
    (is (= ["a" 1] (.next i)))))

(deftest seq-test
  (is (nil? (seq (bean #js {}))))
  (is (nil? (seq (bean #js {} :keywordize-keys false))))
  (is (= () (rest (seq (bean #js {})))))
  (is (= [[:a 1]] (seq (bean #js {:a 1}))))
  (is (map-entry? (first (seq (bean #js {:a 1})))))
  (is (nil? (next (seq (bean #js {:a 1})))))
  (is (= () (rest (seq (bean #js {:a 1})))))
  (is (= [["a" 1]] (seq (bean #js {:a 1} :keywordize-keys false))))
  (is (= [:a] (keys (seq (bean #js {:a 1})))))
  (is (= ["a"] (keys (seq (bean #js {:a 1} :keywordize-keys false)))))
  (is (= [1] (vals (seq (bean #js {:a 1})))))
  (is (= [1] (vals (seq (bean #js {:a 1} :keywordize-keys false))))))

(deftest count-test
  (is (not (counted? (bean #js {:a 1}))))
  (is (== 0 (count (bean #js {}))))
  (is (== 1 (count (bean #js {:a 1}))))
  (is (== 2 (count (bean #js {:a 1, :b 2})))))

(deftest assoc-test
  (let [b (bean #js {:x 1})
        m (assoc b :y 2)]
    (is (map? m))
    (is (= m {:x 1 :y 2})))
  (let [b (bean #js {:x 1} :keywordize-keys false)
        m (assoc b :y 2)]
    (is (map? m))
    (is (= m {"x" 1 :y 2}))))

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
    (is (= [:my-ns/my-name 17] (find b :my-ns/my-name))))
  (let [b (bean #js {"my-ns/my-name" 17} :keywordize-keys false)]
    (is (= ["my-ns/my-name" 17] (find b "my-ns/my-name")))))

(deftest dissoc-test
  (let [b (bean #js {:a 1, :b 2})]
    (is (= {:a 1} (dissoc b :b))))
  (let [b (bean #js {:a 1, :b 2} :keywordize-keys false)]
    (is (= {"a" 1} (dissoc b "b")))))

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
  (let [b (bean color-black :keywordize-keys false)]
    (is (= -16777216 (b "RGB"))))
  (let [b (bean color-black)]
    (is (= ::not-found (b :bogus ::not-found)))))

(deftest editable-collection-test
  (is (= {:a 1, :b 2} (persistent! (assoc! (transient (bean #js {:a 1})) :b 2)))))

(deftest seq-dot-toString-test
  (is (= "([:a 1])" (.toString (seq (bean #js {:a 1})))))
  (is (= "([\"a\" 1])" (.toString (seq (bean #js {:a 1} :keywordize-keys false)))))
  (is (= "([:a 1])" (.toString (seq (bean #js {:a 1} :prop->key prop->key :key->prop key->prop)))))
  (is (= "([:a/b 1])" (.toString (seq (bean #js {"a/b" 1})))))
  (is (= "([\"a/b\" 1])" (.toString (seq (bean #js {"a/b" 1} :keywordize-keys false)))))
  (is (= "([\"a/b\" 1])" (.toString (seq (bean #js {"a/b" 1} :prop->key prop->key :key->prop key->prop))))))

(deftest seq-dot-equiv-test
  (is (.equiv (seq (bean #js {:a 1})) [[:a 1]]))
  (is (.equiv (seq (bean #js {:a 1} :keywordize-keys false)) [["a" 1]]))
  (is (.equiv (seq (bean #js {:a 1} :prop->key prop->key :key->prop key->prop)) [[:a 1]]))
  (is (.equiv (seq (bean #js {"a/b" 1})) [[:a/b 1]]))
  (is (.equiv (seq (bean #js {"a/b" 1} :keywordize-keys false)) [["a/b" 1]]))
  (is (.equiv (seq (bean #js {"a/b" 1} :prop->key prop->key :key->prop key->prop)) [["a/b" 1]])))

(deftest seq-dot-indexOf-test
  (is (zero? (.indexOf (seq (bean #js {:a 1})) [:a 1])))
  (is (zero? (.indexOf (seq (bean #js {:a 1})) [:a 1] 0)))
  (is (== -1 (.indexOf (seq (bean #js {:a 1})) [:a 2])))
  (is (== -1 (.indexOf (seq (bean #js {:a 1})) [:a 1] 1)))
  (is (zero? (.indexOf (seq (bean #js {:a 1} :keywordize-keys false)) ["a" 1])))
  (is (zero? (.indexOf (seq (bean #js {:a 1} :keywordize-keys false)) ["a" 1] 0)))
  (is (== -1 (.indexOf (seq (bean #js {:a 1} :keywordize-keys false)) ["a" 2])))
  (is (== -1 (.indexOf (seq (bean #js {:a 1} :keywordize-keys false)) ["a" 1] 1)))
  (is (zero? (.indexOf (seq (bean #js {:a 1} :prop->key prop->key :key->prop key->prop)) [:a 1])))
  (is (zero? (.indexOf (seq (bean #js {:a 1} :prop->key prop->key :key->prop key->prop)) [:a 1] 0)))
  (is (== -1 (.indexOf (seq (bean #js {:a 1} :prop->key prop->key :key->prop key->prop)) [:a 2])))
  (is (== -1 (.indexOf (seq (bean #js {:a 1} :prop->key prop->key :key->prop key->prop)) [:a 1] 1)))
  (is (zero? (.indexOf (seq (bean #js {"a/b" 1})) [:a/b 1])))
  (is (zero? (.indexOf (seq (bean #js {"a/b" 1})) [:a/b 1] 0)))
  (is (== -1 (.indexOf (seq (bean #js {"a/b" 1})) [:a/b 2])))
  (is (== -1 (.indexOf (seq (bean #js {"a/b" 1})) [:a/b 1] 1)))
  (is (zero? (.indexOf (seq (bean #js {"a/b" 1} :keywordize-keys false)) ["a/b" 1])))
  (is (zero? (.indexOf (seq (bean #js {"a/b" 1} :keywordize-keys false)) ["a/b" 1] 0)))
  (is (== -1 (.indexOf (seq (bean #js {"a/b" 1} :keywordize-keys false)) ["a/b" 2])))
  (is (== -1 (.indexOf (seq (bean #js {"a/b" 1} :keywordize-keys false)) ["a/b" 1] 1)))
  (is (zero? (.indexOf (seq (bean #js {"a/b" 1} :prop->key prop->key :key->prop key->prop)) ["a/b" 1])))
  (is (zero? (.indexOf (seq (bean #js {"a/b" 1} :prop->key prop->key :key->prop key->prop)) ["a/b" 1] 0)))
  (is (== -1 (.indexOf (seq (bean #js {"a/b" 1} :prop->key prop->key :key->prop key->prop)) ["a/b" 2])))
  (is (== -1 (.indexOf (seq (bean #js {"a/b" 1} :prop->key prop->key :key->prop key->prop)) ["a/b" 1] 1))))

(deftest seq-dot-lastIndexOf-test
  (is (zero? (.lastIndexOf (seq (bean #js {:a 1})) [:a 1])))
  (is (zero? (.lastIndexOf (seq (bean #js {:a 1})) [:a 1] 1)))
  (is (== -1 (.lastIndexOf (seq (bean #js {:a 1})) [:a 2])))
  (is (zero? (.lastIndexOf (seq (bean #js {:a 1} :keywordize-keys false)) ["a" 1])))
  (is (zero? (.lastIndexOf (seq (bean #js {:a 1} :keywordize-keys false)) ["a" 1] 1)))
  (is (== -1 (.lastIndexOf (seq (bean #js {:a 1} :keywordize-keys false)) ["a" 2])))
  (is (zero? (.lastIndexOf (seq (bean #js {:a 1} :prop->key prop->key :key->prop key->prop)) [:a 1])))
  (is (zero? (.lastIndexOf (seq (bean #js {:a 1} :prop->key prop->key :key->prop key->prop)) [:a 1] 1)))
  (is (== -1 (.lastIndexOf (seq (bean #js {:a 1} :prop->key prop->key :key->prop key->prop)) [:a 2])))
  (is (zero? (.lastIndexOf (seq (bean #js {"a/b" 1})) [:a/b 1])))
  (is (zero? (.lastIndexOf (seq (bean #js {"a/b" 1})) [:a/b 1] 1)))
  (is (== -1 (.lastIndexOf (seq (bean #js {"a/b" 1})) [:a/b 2])))
  (is (zero? (.lastIndexOf (seq (bean #js {"a/b" 1} :keywordize-keys false)) ["a/b" 1])))
  (is (zero? (.lastIndexOf (seq (bean #js {"a/b" 1} :keywordize-keys false)) ["a/b" 1] 1)))
  (is (== -1 (.lastIndexOf (seq (bean #js {"a/b" 1} :keywordize-keys false)) ["a/b" 2])))
  (is (zero? (.lastIndexOf (seq (bean #js {"a/b" 1} :prop->key prop->key :key->prop key->prop)) ["a/b" 1])))
  (is (zero? (.lastIndexOf (seq (bean #js {"a/b" 1} :prop->key prop->key :key->prop key->prop)) ["a/b" 1] 1)))
  (is (== -1 (.lastIndexOf (seq (bean #js {"a/b" 1} :prop->key prop->key :key->prop key->prop)) ["a/b" 2]))))

(deftest seq-clone-test
  (let [s (seq (bean #js {:a 1}))
        c (clone s)]
    (is (= s c))
    (is (not (identical? s c)))
    (is (nil? (meta c))))
  (let [s (seq (bean #js {:a 1} :keywordize-keys false))
        c (clone s)]
    (is (= s c))
    (is (not (identical? s c)))
    (is (nil? (meta c))))
  (let [s (seq (bean #js {:a 1, "a/b" 2, "d e" 3} :prop->key prop->key :key->prop key->prop))
        c (clone s)]
    (is (= s c))
    (is (not (identical? s c)))
    (is (nil? (meta c))))
  (let [s (with-meta (seq (bean #js {:a 1})) {:foo true})
        c (clone s)]
    (is (= s c))
    (is (not (identical? s c)))
    (is (= {:foo true} (meta c)))))

(deftest seq-meta-test
  (let [s (seq (bean #js {:a 1}))]
    (= {:foo true} (meta (with-meta s {:foo true}))))
  (let [s (seq (bean #js {:a 1} :keywordize-keys false))]
    (= {:foo true} (meta (with-meta s {:foo true})))))

(deftest seq-count-test
  (is (counted? (seq (bean #js {:a 1}))))
  (is (== 0 (count (seq (bean #js {})))))
  (is (== 1 (count (seq (bean #js {:a 1})))))
  (is (== 2 (count (seq (bean #js {:a 1, :b 2})))))
  (is (counted? (rest (seq (bean #js {:a 1, :b 2})))))
  (is (== 1 (count (rest (seq (bean #js {:a 1, :b 2})))))))

(deftest seq-nth-test
  (is (= [:a 1] (nth (seq (bean #js {:a 1})) 0)))
  (is (thrown-with-msg? js/Error #"Index out of bounds" (nth (seq (bean #js {:a 1})) 1)))
  (is (= ::not-found (nth (seq (bean #js {:a 1})) 1 ::not-found))))

(deftest seq-equiv-test
  (is (-equiv (seq (bean #js {:a 1})) [[:a 1]]))
  (is (-equiv (seq (bean #js {:a 1} :keywordize-keys false)) [["a" 1]]))
  (is (-equiv (seq (bean #js {:a 1} :prop->key prop->key :key->prop key->prop)) [[:a 1]]))
  (is (-equiv (seq (bean #js {"a/b" 1})) [[:a/b 1]]))
  (is (-equiv (seq (bean #js {"a/b" 1} :keywordize-keys false)) [["a/b" 1]]))
  (is (-equiv (seq (bean #js {"a/b" 1} :prop->key prop->key :key->prop key->prop)) [["a/b" 1]])))

(deftest seq-conj-test
  (is (= [:x] (conj (seq (bean #js {})) :x)))
  (is (= [:x [:a 1]] (conj (seq (bean #js {:a 1})) :x))))

(deftest seq-empty-test
  (is (= [] (empty (seq (bean #js {:a 1}))))))

(deftest seq-reduce-test
  (is (== 3 (reduce (fn [r e]
                      (+ (cond-> r (map-entry? r) val) (val e)))
              (seq (bean #js {:a 1, :b 2})))))
  (is (== :empty (reduce (fn []
                           :empty)
                   (seq (bean #js {})))))
  (is (== 7 (reduce (fn [r e]
                      (reduced 7))
              (seq (bean #js {:a 1, :b 2})))))
  (is (== 3 (reduce (fn [r e]
                      (+ r (val e)))
              0
              (seq (bean #js {:a 1, :b 2})))))
  (is (== 0 (reduce (fn [r e]
                      (+ r (val e)))
              0
              (seq (bean #js {})))))
  (is (== 7 (reduce (fn [r e]
                      (reduced 7))
              0
              (seq (bean #js {:a 1, :b 2}))))))

(deftest seq-hash-test
  (is (== (hash (seq {:a 1})) (hash (seq (bean #js {:a 1})))))
  (is (== (hash (seq {"a" 1})) (hash (seq (bean #js {:a 1} :keywordize-keys false))))))
