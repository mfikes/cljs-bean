(ns cljs-bean.core-test
  (:require
   [clojure.test :refer [are deftest is]]
   [cljs-bean.core :refer [bean bean? object]]))

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

(deftest empty-bean-test
  (is (bean? (bean)))
  (is (empty? (bean)))
  (is (= {:a 1} (assoc (bean) :a 1))))

(defrecord Lu [fqn])

(deftest keyword-lookup-test
  (let [b (bean #js {:a 1})]
    (is (contains? b :a))
    (is (== 1 (:a b)))
    (is (== 1 (b :a)))
    (is (not (contains? b (->Lu "a"))))
    (is (nil? (b (->Lu "a"))))))

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
        _ (count o)
        c (clone o)]
    (is (= o c))
    (is (not (identical? o c)))
    (is (nil? (meta c)))
    (is (== 1 (count c))))
  (let [o (bean #js {:a 1} :keywordize-keys false)
        _ (count o)
        c (clone o)]
    (is (= o c))
    (is (not (identical? o c)))
    (is (nil? (meta c)))
    (is (== 1 (count c))))
  (let [o (bean #js {:a 1, "a/b" 2, "d e" 3} :prop->key prop->key :key->prop key->prop)
        _ (count o)
        c (clone o)]
    (is (= o c))
    (is (not (identical? o c)))
    (is (nil? (meta c)))
    (is (== 3 (count o))))
  (let [o (with-meta (bean #js {:a 1}) {:foo true})
        _ (count o)
        c (clone o)]
    (is (= o c))
    (is (not (identical? o c)))
    (is (= {:foo true} (meta c)))
    (is (== 1 (count c)))))

(deftest meta-test
  (let [o (bean #js {:a 1})]
    (count o)
    (is (= {:foo true} (meta (with-meta o {:foo true}))))
    (is (== 1 (count (with-meta o {:foo true})))))
  (let [o (bean #js {:a 1} :keywordize-keys false)]
    (count o)
    (is (= {:foo true} (meta (with-meta o {:foo true}))))
    (is (== 1 (count (with-meta o {:foo true})))))
  (let [m {:x 1}]
    (is (= m (meta (dissoc (with-meta (bean #js {:a 1}) m) :a))))
    (is (= m (meta (dissoc (with-meta (bean #js {:a 1}) m) :b)))))
  (let [m {:x 1}]
    (is (= m (meta (assoc (with-meta (bean #js {:a 1}) m) :b 2))))
    (is (= m (meta (assoc (with-meta (bean #js {:a 1}) m) :a 1))))
    (is (= m (meta (assoc (with-meta (bean #js {:a 1}) m) "c" 1)))))
  (let [m {:x 1}]
    (is (= m (meta (conj (with-meta (bean #js {:a 1}) m) [:b 2]))))
    (is (= m (meta (conj (with-meta (bean #js {:a 1}) m) [:a 1]))))))

(deftest conj-test
  (let [b (bean #js {:x 1})
        m (conj b [:y 2])
        o (object m)]
    (is (map? m))
    (is (bean m))
    (is (= {:x 1 :y 2} m))
    (is (#{["x" "y"] ["y" "x"]} (vec (js-keys o)))))
  (let [b (bean #js {:x 1})
        m (conj b ["y" 2])]
    (is (map? m))
    (is (not (bean? m)))
    (is (= {:x 1 "y" 2} m))))

(deftest empty-test
  (is (= {} (empty (bean #js {:a 1}))))
  (is (zero? (count (empty (bean #js {:a 1})))))
  (is (empty? (empty (bean #js {:a 1}))))
  (is (bean? (empty (bean #js {:a 1}))))
  (let [m (with-meta (bean #js {:a 1}) {:foo true})]
    (= {:foo true} (meta (empty m))))
  (is (= {:a 1} (assoc (empty (bean #js {:b 2})) :a 1)))
  (is (not (bean? (assoc (bean) "a" 1))))
  (is (= {"a" 1} (assoc (bean) "a" 1))))

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
  (is (counted? (bean)))
  (is (== 0 (count (bean #js {}))))
  (is (== 1 (count (bean #js {:a 1}))))
  (is (== 2 (count (bean #js {:a 1, :b 2}))))
  (is (== 1 (count (assoc (bean) :a 1))))
  (is (== 1 (count (assoc (bean #js {:a 1}) :a 1))))
  (is (== 1 (count (-> (bean) (assoc :a 1) (dissoc :b)))))
  (is (== 0 (count (-> (bean) (assoc :a 1) (dissoc :a))))))

(deftest assoc-test
  (let [b (bean #js {:x 1})
        m (assoc b :y 2)
        o (object m)]
    (is (map? m))
    (is (bean? m))
    (is (= {:x 1 :y 2} m))
    (is (== 1 (unchecked-get o "x")))
    (is (== 2 (unchecked-get o "y"))))
  (let [b (bean #js {:x 1})
        m (assoc b "y" 2)]
    (is (map? m))
    (is (not (bean? m)))
    (is (= {:x 1 "y" 2} m)))
  (let [b (bean #js {:x 1} :keywordize-keys false)
        m (assoc b "y" 2)
        o (object m)]
    (is (map? m))
    (is (bean? m))
    (is (= {"x" 1 "y" 2} m))
    (is (== 1 (unchecked-get o "x")))
    (is (== 2 (unchecked-get o "y"))))
  (let [b (bean #js {:x 1} :keywordize-keys false)
        m (assoc b :y 2)]
    (is (map? m))
    (is (not (bean? m)))
    (is (= {"x" 1 :y 2} m))))

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
  (let [b (bean #js {:a 1, :b 2})
        m (dissoc b :b)
        o (object m)]
    (is (= {:a 1} m))
    (is (bean? m))
    (is (= ["a"] (vec (js-keys o))))
    (is (== 1 (unchecked-get o "a"))))
  (let [b (bean #js {:a 1, :b 2})
        m (dissoc b "c")
        o (object m)]
    (is (= {:a 1, :b 2} m))
    (is (bean? m))
    (is (#{["a" "b"] ["b" "a"]} (vec (js-keys o))))
    (is (== 1 (unchecked-get o "a")))
    (is (== 2 (unchecked-get o "b"))))
  (let [b (bean #js {:a 1, :b 2} :keywordize-keys false)
        m (dissoc b "b")
        o (object m)]
    (is (= {"a" 1} m))
    (is (bean? m))
    (is (= ["a"] (vec (js-keys o))))
    (is (== 1 (unchecked-get o "a"))))
  (let [b (bean #js {:a 1, :b 2} :keywordize-keys false)
        m (dissoc b :c)
        o (object m)]
    (is (= {"a" 1, "b" 2} m))
    (is (bean? m))
    (is (#{["a" "b"] ["b" "a"]} (vec (js-keys o))))
    (is (== 1 (unchecked-get o "a")))
    (is (== 2 (unchecked-get o "b")))))

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

(deftest into-test
  (is (bean? (into (bean #js {}) {:a 1})))
  (is (not (bean? (into (bean #js {}) [[:a 1] ["b" 2]]))))
  (is (= {:a 1, "b" 2} (into (bean #js {}) [[:a 1] ["b" 2]])))
  (is (not (bean? (into (bean #js {}) [["a" 1] [:b 2]]))))
  (is (= {"a" 1, :b 2} (into (bean #js {}) [["a" 1] [:b 2]])))
  (is (= {"a" 1, "b" 2} (into (bean #js {:a 1} :keywordize-keys false) {"b" 2}))))

(deftest bean?-test
  (is (bean? (bean #js {:a 1})))
  (is (not (bean? {:a 1}))))

(deftest object-test
  (is (object? (object (bean #js {}))))
  (let [o1 #js {:a 1}
        o2 (object (bean o1))]
    (is (identical? o2 o1)))
  (is (== 1 (unchecked-get (object (bean #js {:a 1})) "a"))))

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

(deftest transient-test
  (is (bean? (persistent! (transient (bean)))))
  (let [t (transient (bean))]
    (persistent! t)
    (is (thrown-with-msg? js/Error #"persistent! called twice" (persistent! t)))))

(deftest transient-count-test
  (is (counted? (transient (bean))))
  (is (== 0 (count (transient (bean)))))
  (is (== 0 (count (transient (bean #js {})))))
  (is (== 1 (count (transient (bean #js {:a 1})))))
  (is (== 2 (count (transient (bean #js {:a 1, :b 2})))))
  (let [b (bean #js {})]
    (count b)
    (is (== 0 (count (transient b)))))
  (let [b (bean #js {:a 1})]
    (count b)
    (is (== 1 (count (transient b)))))
  (let [b (bean #js {:a 1, :b 2})]
    (count b)
    (is (== 2 (count (transient b)))))
  (is (== 1 (count (assoc! (transient (bean)) :a 1))))
  (is (== 1 (count (assoc! (transient (bean #js {:a 1})) :a 1))))
  (let [b (bean #js {})]
    (count b)
    (is (== 1 (count (assoc! (transient b) :a 1)))))
  (let [b (bean #js {:a 1})]
    (count b)
    (is (== 1 (count (assoc! (transient b) :a 1)))))
  (is (== 1 (count (-> (transient (bean)) (assoc! :a 1) (dissoc! :b)))))
  (is (== 0 (count (-> (transient (bean)) (assoc! :a 1) (dissoc! :a)))))
  (is (== 1 (count (persistent! (-> (transient (bean)) (assoc! :a 1)))))))

(deftest assoc!-test
  (is (= {:a 1} (persistent! (assoc! (transient (bean)) :a 1))))
  (is (= {:a 1, :b 2} (persistent! (assoc! (transient (bean)) :a 1 :b 2))))
  (is (= {"a" 1} (persistent! (assoc! (transient (bean)) "a" 1))))
  (is (not (bean? (persistent! (assoc! (transient (bean)) "a" 1)))))
  (let [t (doto (assoc! (transient (bean)) :a 1) persistent!)]
    (is (thrown-with-msg? js/Error #"assoc! after persistent!" (assoc! t :x 1)))))

(deftest conj!-test
  (is (= {:a 1} (persistent! (conj! (transient (bean)) [:a 1]))))
  (is (= {:a 1} (persistent! (conj! (transient (bean)) {:a 1}))))
  (is (= {:a 1, :b 2} (persistent! (conj! (transient (bean)) {:a 1, :b 2}))))
  (is (= {"a" 1} (persistent! (conj! (transient (bean)) ["a" 1]))))
  (is (not (bean? (persistent! (conj! (transient (bean)) ["a" 1])))))
  (let [t (doto (conj! (transient (bean)) [:a 1]) persistent!)]
    (is (thrown-with-msg? js/Error #"conj! after persistent!" (conj! t :x 1)))))

(deftest dissoc!-test
  (is (= {:a 1} (persistent! (dissoc! (transient (bean #js {:a 1 :b 2})) :b))))
  (is (= {} (persistent! (dissoc! (transient (bean #js {:a 1 :b 2})) :a :b))))
  (let [t (doto (dissoc! (transient (bean #js {:a 1, :b 2})) :a) persistent!)]
    (is (thrown-with-msg? js/Error #"dissoc! after persistent!" (dissoc! t :b)))))

(deftest transient-lookup-test
  (is (== 1 (:a (assoc! (transient (bean)) :a 1))))
  (let [t (assoc! (transient (bean)) :a 1)]
    (persistent! t)
    (is (thrown-with-msg? js/Error #"lookup after persistent!" (:a t))))
  (is (= :not-found (:b (assoc! (transient (bean)) :a 1) :not-found)))
  (let [t (assoc! (transient (bean)) :a 1)]
    (persistent! t)
    (is (thrown-with-msg? js/Error #"lookup after persistent!" (:a t :not-found)))))

(deftest transient-invoke-test
  (is (== 1 ((assoc! (transient (bean)) :a 1) :a)))
  (is (= :not-found ((assoc! (transient (bean)) :a 1) :b :not-found))))

