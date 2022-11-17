(ns cljs-bean.core-test
  (:require
    [clojure.test :refer [are deftest is testing]]
    [clojure.test.check :as tc]
    [clojure.test.check.clojure-test :refer [defspec]]
    [clojure.test.check.generators :as gen]
    [clojure.test.check.properties :as prop :include-macros true]
    [cljs-bean.core :refer [bean bean? object ->clj ->js]]
    [cljs-bean.from.cljs.core-test :as core-test]
    [clojure.walk :as walk]
    [goog.object :as gobj]))

(defn recursive-bean? [x]
  (and (bean? x) (.-recursive? x)))

(defn js?
  "Returns true if x is recursively purely a JavaScript value."
  [x]
  (cond
    (nil? x) true
    (boolean? x) true
    (string? x) true
    (number? x) true
    (fn? x) true
    (array? x) (every? js? x)
    (object? x) (every? js? (map #(gobj/get x %) (js-keys x)))))

(defn array-vector? [x]
  (instance? @#'cljs-bean.core/ArrayVector x))

(defn js-able?
  "Returns true for objects produced by this lib that can be converted to JavaScript in constant time."
  [x]
  (cond
    (bean? x) (js? (object x))
    (array-vector? x) (js? (.-arr x))
    :else false))

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
  (is (= "{:a #js {:b 3}}" (.toString (bean #js {:a #js {:b 3}}))))
  (is (= "{:a {:b 3}}" (.toString (bean #js {:a #js {:b 3}} :recursive true))))
  (is (= "{\"a\" 1}" (.toString (bean #js {:a 1} :keywordize-keys false))))
  (is (= "{:a 1}" (.toString (bean #js {:a 1} :prop->key prop->key :key->prop key->prop))))
  (is (#{"#:a{:b 1}" "{:a/b 1}"} (.toString (bean #js {"a/b" 1}))))
  (is (= "{\"a/b\" 1}" (.toString (bean #js {"a/b" 1} :keywordize-keys false))))
  (is (= "{\"a/b\" 1}" (.toString (bean #js {"a/b" 1} :prop->key prop->key :key->prop key->prop)))))

(deftest dot-equiv-test
  (is (.equiv (bean #js {:a 1}) {:a 1}))
  (is (.equiv (bean #js {:a #js {:b 3}} :recursive true) {:a {:b 3}}))
  (is (.equiv (bean #js {:a 1} :keywordize-keys false) {"a" 1}))
  (is (.equiv (bean #js {:a 1} :prop->key prop->key :key->prop key->prop) {:a 1}))
  (is (.equiv (bean #js {"a/b" 1}) {:a/b 1}))
  (is (.equiv (bean #js {"a/b" 1} :keywordize-keys false) {"a/b" 1}))
  (is (.equiv (bean #js {"a/b" 1} :prop->key prop->key :key->prop key->prop) {"a/b" 1})))

(deftest test-es6-interfaces
  (testing "ES6 collection interfaces"
    (is (.has (bean #js {:foo "bar"}) :foo))
    (is (= (.get (bean #js {:foo "bar"}) :foo) "bar"))
    (is (= (.get (bean #js {:foo #js {:x 1}} :recursive true) :foo) {:x 1}))
    (is (= (.get (bean #js {:foo "bar"}) :bar :default) :default))
    (let [iter (.keys (bean #js {:foo "bar" :baz "woz"}))]
      (testing "map key iteration"
        (is (#{:foo :baz} (.-value (.next iter))))
        (is (#{:foo :baz} (.-value (.next iter))))
        (is (.-done (.next iter)))))
    (let [either (.entries (bean #js {:foo "bar" :baz "woz"}))]
      (testing "map entry iteration"
        (let [entries #{(seq #js [:foo "bar"]) (seq #js [:baz "woz"])}]
          (is (entries (seq (.-value (.next either)))))
          (is (entries (seq (.-value (.next either))))))
        (is (.-done (.next either)))))
    (let [iter (.values (bean #js {:foo "bar" :baz "woz"}))]
      (testing "map value iteration"
        (is (#{"bar" "woz"} (.-value (.next iter))))
        (is (#{"bar" "woz"} (.-value (.next iter))))
        (is (.-done (.next iter)))))
    (let [iter (.values (bean #js {:foo #js {:x 1} :baz #js {:y 2}} :recursive true))]
      (testing "map value iteration"
        (is (#{{:x 1} {:y 2}} (.-value (.next iter))))
        (is (#{{:x 1} {:y 2}} (.-value (.next iter))))
        (is (.-done (.next iter)))))))

(deftest dot-forEach-test
  (.forEach (bean #js {:a 1}) (fn [v k]
                                (is (= :a k))
                                (is (== 1 v)))))

(deftest keys-test
  (is (= [:a] (keys (bean #js {:a 1}))))
  (is (= ["a"] (keys (bean #js {:a 1} :keywordize-keys false))))
  (is (= [:a] (keys (bean #js {:a 1} :prop->key prop->key :key->prop key->prop))))
  (is (= [:a/b] (keys (bean #js {"a/b" 1}))))
  (is (= ["a/b"] (keys (bean #js {"a/b" 1} :keywordize-keys false))))
  (is (= ["a/b"] (keys (bean #js {"a/b" 1} :prop->key prop->key :key->prop key->prop)))))

(deftest vals-test
  (is (= [1] (vals (bean #js {:a 1}))))
  (is (= [{:b 2}] (vals (bean #js {:a #js {:b 2}} :recursive true)))))

(deftest clone-test
  (let [o (bean #js {:a 1})
        _ (count o)
        c (clone o)]
    (is (= o c))
    (is (not (identical? o c)))
    (is (nil? (meta c)))
    (is (== 1 (count c))))
  (let [o (bean #js {:a #js {:b 2}} :recursive true)
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
  (let [o (bean #js {:a #js {:2 2}} :recursive true)]
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
  (let [b (bean #js {:x #js {:z 4}} :recursive true)
        m (conj b [:y (bean #js {:w 10})])
        o (object m)]
    (is (map? m))
    (is (bean m))
    (is (= {:x {:z 4} :y {:w 10}} m))
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
  (let [b (empty (bean #js {:a #js {:b 2}} :recursive true))]
    (is (empty? b))
    (is (= {:x {:y 10}} (assoc b :x (bean #js {:y 10})))))
  (let [m (with-meta (bean #js {:a 1}) {:foo true})]
    (= {:foo true} (meta (empty m))))
  (is (= {:a 1} (assoc (empty (bean #js {:b 2})) :a 1)))
  (is (not (bean? (assoc (bean) "a" 1))))
  (is (= {"a" 1} (assoc (bean) "a" 1))))

(deftest equiv-test
  (is (-equiv (bean #js {:a 1}) {:a 1}))
  (is (-equiv (bean #js {:a #js {:b 2}} :recursive true) {:a {:b 2}}))
  (is (-equiv (bean #js {:a 1} :keywordize-keys false) {"a" 1}))
  (is (-equiv (bean #js {:a 1} :prop->key prop->key :key->prop key->prop) {:a 1}))
  (is (-equiv (bean #js {"a/b" 1}) {:a/b 1}))
  (is (-equiv (bean #js {"a/b" 1} :keywordize-keys false) {"a/b" 1}))
  (is (-equiv (bean #js {"a/b" 1} :prop->key prop->key :key->prop key->prop) {"a/b" 1})))

(deftest hash-test
  (is (== (hash {:a 1}) (hash (bean #js {:a 1}))))
  (is (== (hash {:a {:b 2}}) (hash (bean #js {:a #js {:b 2}} :recursive true))))
  (is (== (hash {"a" 1}) (hash (bean #js {:a 1} :keywordize-keys false)))))

(deftest seq-test
  (is (nil? (seq (bean #js {}))))
  (is (nil? (seq (bean #js {} :keywordize-keys false))))
  (is (= () (rest (seq (bean #js {})))))
  (is (= [[:a 1]] (seq (bean #js {:a 1}))))
  (is (= [[:a {:b 2}]] (seq (bean #js {:a #js {:b 2}} :recursive true))))
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
  (is (== 1 (count (bean #js {:a #js {:b 2}} :recursive true))))
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
  (let [b (bean #js {:x 1} :recursive true)
        m (assoc b :y 2)
        o (object m)]
    (is (map? m))
    (is (bean? m))
    (is (= {:x 1 :y 2} m))
    (is (== 1 (unchecked-get o "x")))
    (is (== 2 (unchecked-get o "y"))))
  (let [b (bean #js {:x 1} :recursive true)
        m (assoc b :y {:z 3})]
    (is (map? m))
    (is (not (bean? m)))
    (is (= {:x 1 :y {:z 3}} m)))
  (let [b (bean #js {:x 1})
        m (assoc b "y" 2)]
    (is (map? m))
    (is (not (bean? m)))
    (is (= {:x 1 "y" 2} m)))
  (let [b (bean #js {:x 1} :recursive true)
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
    (is (= {"x" 1 :y 2} m)))
  (is (js-able? (assoc (bean #js {} :recursive true) :a (bean #js {:b 2}))))
  (is (not (js-able? (assoc (bean #js {} :recursive true) :a {:b 2})))))

(deftest contains?-test
  (let [b (bean color-black)]
    (is (contains? b :red))
    (is (not (contains? b "red")))
    (is (not (contains? b :missing)))))

(deftest find-test
  (let [b (bean color-black)]
    (is (map-entry? (find b :red)))
    (is (= [:red 0] (find b :red))))
  (let [b (bean #js {:a #js {:b 2}} :recursive true)]
    (is (map-entry? (find b :a)))
    (is (= [:a {:b 2}] (find b :a)))
    (is (bean? (val (find b :a)))))
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
  (let [b (bean #js {:a #js {:z 10}, :b #js {:c 3}} :recursive true)
        m (dissoc b :b)
        o (object m)]
    (is (= {:a {:z 10}} m))
    (is (bean? m))
    (is (= ["a"] (vec (js-keys o))))
    (is (object? (unchecked-get o "a"))))
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
  (is (= {1 :a, {:z 10} :b, 3 :c}
        (reduce-kv #(assoc %1 %3 %2) {} (bean #js {:a 1 :b #js {:z 10} :c 3} :recursive true))))
  (is (= 1 (reduce-kv (fn [r k v] (reduced v))
             nil
             (bean #js {:a 1}))))
  (is (= {:b 2} (reduce-kv (fn [r k v] (reduced v))
             nil
             (bean #js {:a #js {:b 2}} :recursive true)))))

(deftest reduce-test
  (is (= [:a 1 :b 2 :c 3]
        (reduce into (bean #js {:a 1, :b 2, :c 3}))))
  (is (= [:a 1 :b {:z 10} :c 3]
        (reduce into (bean #js {:a 1, :b #js {:z 10}, :c 3} :recursive true))))
  (is (= {:a 2, :b 3}
        (reduce (fn [r [k v]]
                  (assoc r k (inc v)))
          {}
          (bean #js {:a 1, :b 2}))))
  (is (= {:a {:z 1}, :b {:z 11}}
        (reduce (fn [r [k v]]
                  (assoc r k (update v :z inc)))
          {}
          (bean #js {:a #js {:z 0}, :b #js {:z 10}} :recursive true)))))

(deftest ifn-test
  (let [b (bean color-black)]
    (is (= -16777216 (b :RGB))))
  (let [b (bean #js {:a #js {:b 2}} :recursive true)]
    (is (= {:b 2} (b :a))))
  (let [b (bean color-black :keywordize-keys false)]
    (is (= -16777216 (b "RGB"))))
  (let [b (bean color-black)]
    (is (= ::not-found (b :bogus ::not-found)))))

(deftest into-test
  (is (bean? (into (bean #js {}) {:a 1})))
  (is (bean? (into (bean #js {} :recursive true) {:a 1})))
  (is (not (bean? (into (bean #js {}) [[:a 1] ["b" 2]]))))
  (is (= {:a 1, :b 2} (into (bean #js {}) [[:a 1] [:b 2]])))
  (is (= {:a 1, :b {:z 10}} (into (bean #js {} :recursive true) [[:a 1] [:b (bean #js {:z 10})]])))
  (is (= {:a 1, "b" 2} (into (bean #js {}) [[:a 1] ["b" 2]])))
  (is (not (bean? (into (bean #js {}) [["a" 1] [:b 2]]))))
  (is (= {"a" 1, :b 2} (into (bean #js {}) [["a" 1] [:b 2]])))
  (is (= {"a" 1, "b" 2} (into (bean #js {:a 1} :keywordize-keys false) {"b" 2}))))

(deftest bean?-test
  (is (bean? (bean #js {:a 1})))
  (is (not (bean? {:a 1}))))

(deftest object-test
  (is (object? (object (bean #js {}))))
  (is (object? (object (bean #js {} :recursive true))))
  (let [o1 #js {:a 1}
        o2 (object (bean o1))]
    (is (identical? o2 o1)))
  (let [o1 #js {:a #js {:b 2}}
        o2 (object (bean o1))]
    (is (identical? o2 o1)))
  (is (== 1 (unchecked-get (object (bean #js {:a 1})) "a"))))

(deftest seq-dot-toString-test
  (is (= "([:a 1])" (.toString (seq (bean #js {:a 1})))))
  (is (= "([:a {:b 2}])" (.toString (seq (bean #js {:a #js {:b 2}} :recursive true)))))
  (is (= "([\"a\" 1])" (.toString (seq (bean #js {:a 1} :keywordize-keys false)))))
  (is (= "([:a 1])" (.toString (seq (bean #js {:a 1} :prop->key prop->key :key->prop key->prop)))))
  (is (= "([:a/b 1])" (.toString (seq (bean #js {"a/b" 1})))))
  (is (= "([\"a/b\" 1])" (.toString (seq (bean #js {"a/b" 1} :keywordize-keys false)))))
  (is (= "([\"a/b\" 1])" (.toString (seq (bean #js {"a/b" 1} :prop->key prop->key :key->prop key->prop))))))

(deftest seq-dot-equiv-test
  (is (.equiv (seq (bean #js {:a 1})) [[:a 1]]))
  (is (.equiv (seq (bean #js {:a #js {:b 2}} :recursive true)) [[:a {:b 2}]]))
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
  (is (== -1 (.indexOf (seq (bean #js {:a #js {:b 2}} :recursive true)) [:a {:b 2}] 1)))
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

(deftest seq-seq-test
  (is (seqable? (seq (bean #js {:a 1}))))
  (is (= [[:a 1]] (seq (seq (bean #js {:a 1}))))))

(deftest seq-count-test
  (is (counted? (seq (bean #js {:a 1}))))
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
  (is (= [] (empty (seq (bean #js {:a 1})))))
  (is (= [] (empty (seq (bean #js {:a #js {:b 10}} :recursive true))))))

(deftest seq-reduce-test
  (is (== 3 (reduce (fn [r e]
                      (+ (cond-> r (map-entry? r) val) (val e)))
              (seq (bean #js {:a 1, :b 2})))))
  (is (== 3 (reduce (fn [r e]
                      (+ (:z (cond-> r (map-entry? r) val)) (:z (val e))))
              (seq (bean #js {:a #js {:z 1}, :b #js {:z 2}} :recursive true)))))
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
  (is (== (hash (seq {:a {:b 2}})) (hash (seq (bean #js {:a #js {:b 2}} :recursive true)))))
  (is (== (hash (seq {"a" 1})) (hash (seq (bean #js {:a 1} :keywordize-keys false))))))

(deftest coll-iter-seq-match
  (is (core-test/seq-iter-match (bean)))
  (is (core-test/seq-iter-match (bean #js {:a 1})))
  (is (core-test/seq-iter-match (bean #js {:a #js {:b 2}} :recursive true)))
  (is (core-test/seq-iter-match (bean #js {:a 1, :b 2})))
  (is (core-test/seq-iter-match (bean #js {:a 1, :b #js {:c 3}} :recursive true)))
  (is (core-test/seq-iter-match (bean #js {} :keywordize-keys false)))
  (is (core-test/seq-iter-match (bean #js {:a 1} :keywordize-keys false)))
  (is (core-test/seq-iter-match (bean #js {:a 1, :b 2} :keywordize-keys false)))
  (is (core-test/seq-iter-match (bean #js {} :prop->key prop->key :key->prop key->prop)))
  (is (core-test/seq-iter-match (bean #js {:a 1} :prop->key prop->key :key->prop key->prop)))
  (is (core-test/seq-iter-match (bean #js {:a 1, :b 2} :prop->key prop->key :key->prop key->prop))))

(deftest iter-test
  (is (iterable? (bean #js {:a 1})))
  (is (some? (iter (bean #js {:a 1}))))
  (is (some? (iter (bean #js {:a #js {:b 2}} :recursive true))))
  (is (= '[:a] (sequence (map key) (bean #js {:a 1}))))
  (is (= '[1] (sequence (map val) (bean #js {:a 1})))))

(deftest transient-test
  (is (bean? (persistent! (transient (bean)))))
  (let [t (transient (bean))]
    (persistent! t)
    (is (thrown-with-msg? js/Error #"persistent! called twice" (persistent! t)))))

(deftest transient-count-test
  (is (counted? (transient (bean))))
  (is (== 0 (count (transient (bean)))))
  (is (== 0 (count (transient (bean #js {})))))
  (is (== 0 (count (transient (bean #js {} :recursive true)))))
  (is (== 1 (count (transient (bean #js {:a 1})))))
  (is (== 1 (count (transient (bean #js {:a #js {:b 2}} :recursive true)))))
  (is (== 2 (count (transient (bean #js {:a 1, :b 2})))))
  (is (== 2 (count (transient (bean #js {:a 1, :b #js {:c 3}} :recursive true)))))
  (let [b (bean #js {})]
    (count b)
    (is (== 0 (count (transient b)))))
  (let [b (bean #js {} :recursive true)]
    (count b)
    (is (== 0 (count (transient b)))))
  (let [b (bean #js {:a 1})]
    (count b)
    (is (== 1 (count (transient b)))))
  (let [b (bean #js {:a #js {:b 2}} :recursive true)]
    (count b)
    (is (== 1 (count (transient b)))))
  (let [b (bean #js {:a 1, :b 2})]
    (count b)
    (is (== 2 (count (transient b)))))
  (let [b (bean #js {:a 1, :b #js {:c 3}} :recursive true)]
    (count b)
    (is (== 2 (count (transient b)))))
  (is (== 1 (count (assoc! (transient (bean)) :a 1))))
  (is (== 1 (count (assoc! (transient (bean #js {} :recursive true)) :a 1))))
  (is (== 1 (count (assoc! (transient (bean #js {} :recursive true)) :a (bean #js {:b 2})))))
  (is (== 1 (count (assoc! (transient (bean #js {:a 1})) :a 1))))
  (is (== 1 (count (assoc! (transient (bean #js {:a 1} :recursive true)) :a (bean #js {:b 2})))))
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
  (is (= {:a 1} (persistent! (assoc! (transient (bean #js {} :recursive true)) :a 1))))
  (is (= {:a {:b 2}} (persistent! (assoc! (transient (bean #js {} :recursive true)) :a (bean #js {:b 2})))))
  (is (js-able? (persistent! (assoc! (transient (bean #js {} :recursive true)) :a (bean #js {:b 2})))))
  (is (not (js-able? (persistent! (assoc! (transient (bean #js {} :recursive true)) :a {:b 2})))))
  (is (= {:a 1, :b 2} (persistent! (assoc! (transient (bean)) :a 1 :b 2))))
  (is (= {"a" 1} (persistent! (assoc! (transient (bean)) "a" 1))))
  (is (not (bean? (persistent! (assoc! (transient (bean)) "a" 1)))))
  (let [t (doto (assoc! (transient (bean)) :a 1) persistent!)]
    (is (thrown-with-msg? js/Error #"assoc! after persistent!" (assoc! t :x 1)))))

(deftest conj!-test
  (is (= {:a 1} (persistent! (conj! (transient (bean)) [:a 1]))))
  (is (= {:a 1} (persistent! (conj! (transient (bean #js {} :recursive true)) [:a 1]))))
  (is (= {:a {:b 2}} (persistent! (conj! (transient (bean #js {} :recursive true)) [:a (bean #js {:b 2})]))))
  (is (= {:a 1} (persistent! (conj! (transient (bean)) {:a 1}))))
  (is (= {:a 1, :b 2} (persistent! (conj! (transient (bean)) {:a 1, :b 2}))))
  (is (= {"a" 1} (persistent! (conj! (transient (bean)) ["a" 1]))))
  (is (not (bean? (persistent! (conj! (transient (bean)) ["a" 1])))))
  (let [t (doto (conj! (transient (bean)) [:a 1]) persistent!)]
    (is (thrown-with-msg? js/Error #"conj! after persistent!" (conj! t :x 1)))))

(deftest dissoc!-test
  (is (= {:a 1} (persistent! (dissoc! (transient (bean #js {:a 1 :b 2})) :b))))
  (is (= {:a 1} (persistent! (dissoc! (transient (bean #js {:a 1 :b #js {:x 10}} :recursive true)) :b))))
  (is (= {} (persistent! (dissoc! (transient (bean #js {:a 1 :b 2})) :a :b))))
  (let [t (doto (dissoc! (transient (bean #js {:a 1, :b 2})) :a) persistent!)]
    (is (thrown-with-msg? js/Error #"dissoc! after persistent!" (dissoc! t :b)))))

(deftest transient-lookup-test
  (is (== 1 (:a (assoc! (transient (bean)) :a 1))))
  (is (== 1 (:a (assoc! (transient (bean #js {} :recursive true)) :a 1))))
  (is (== {:b 2} (:a (assoc! (transient (bean #js {} :recursive true)) :a (bean #js {:b 2})))))
  (let [t (assoc! (transient (bean)) :a 1)]
    (persistent! t)
    (is (thrown-with-msg? js/Error #"lookup after persistent!" (:a t))))
  (is (= :not-found (:b (assoc! (transient (bean)) :a 1) :not-found)))
  (let [t (assoc! (transient (bean)) :a 1)]
    (persistent! t)
    (is (thrown-with-msg? js/Error #"lookup after persistent!" (:a t :not-found)))))

(deftest transient-invoke-test
  (is (== 1 ((assoc! (transient (bean)) :a 1) :a)))
  (is (== 1 ((assoc! (transient (bean #js {} :recursive true)) :a 1) :a)))
  (is (= :not-found ((assoc! (transient (bean)) :a 1) :b :not-found))))

(deftest object-hint-test
  (let [b (bean #js {:myInc (fn [x] (inc x))})]
    (is (= 2 (.myInc (object b) 1)))))

(deftest vec-dot-toString-test
  (is (= "[]" (.toString (->clj #js []))))
  (is (= "[1]" (.toString (->clj #js [1]))))
  (is (= "[1 2]" (.toString (->clj #js [1 2]))))
  (is (= "[[]]" (.toString (->clj #js [#js []]))))
  (is (= "[[1]]" (.toString (->clj #js [#js [1]]))))
  (is (= "[{:a 1}]" (.toString (->clj #js [#js {:a 1}])))))

(deftest vec-dot-equiv-test
  (is (.equiv (->clj #js []) []))
  (is (.equiv (->clj #js [1]) [1]))
  (is (.equiv (->clj #js [1 2]) [1 2]))
  (is (.equiv (->clj #js [#js []]) [[]]))
  (is (.equiv (->clj #js [#js [1]]) [[1]]))
  (is (.equiv (->clj #js [#js {:a 1}]) [{:a 1}])))

(deftest vec-dot-indexOf-test
  (is (zero? (.indexOf (->clj #js [0]) 0)))
  (is (zero? (.indexOf (->clj #js [0]) 0 0)))
  (is (== -1 (.indexOf (->clj #js [0]) 1)))
  (is (== -1 (.indexOf (->clj #js [0]) 1 1)))
  (is (zero? (.indexOf (->clj #js [#js {:a 1}]) {:a 1})))
  (is (zero? (.indexOf (->clj #js [#js {:a 1}]) {:a 1} 0)))
  (is (== -1 (.indexOf (->clj #js [#js {:a 1}]) {:a 2})))
  (is (== -1 (.indexOf (->clj #js [#js {:a 1}]) {:a 2} 1)))
  (is (== -1 (.indexOf (->clj #js [#js {:a 1}]) {:a 1} 1))))

(deftest vec-dot-lastIndexOf-test
  (is (zero? (.lastIndexOf (->clj #js [0]) 0)))
  (is (zero? (.lastIndexOf (->clj #js [0]) 0 1)))
  (is (== -1 (.lastIndexOf (->clj #js [0]) 1)))
  (is (== -1 (.lastIndexOf (->clj #js [0]) 1 1)))
  (is (zero? (.lastIndexOf (->clj #js [#js {:a 1}]) {:a 1})))
  (is (zero? (.lastIndexOf (->clj #js [#js {:a 1}]) {:a 1} 0)))
  (is (== -1 (.lastIndexOf (->clj #js [#js {:a 1}]) {:a 2})))
  (is (== -1 (.lastIndexOf (->clj #js [#js {:a 1}]) {:a 2} 1)))
  (is (zero? (.lastIndexOf (->clj #js [#js {:a 1}]) {:a 1} 1))))

(deftest vec-clone-test
  (let [v (->clj #js [1])
        _ (count v)
        c (clone v)]
    (is (= v c))
    (is (not (identical? v c)))
    (is (nil? (meta c)))
    (is (== 1 (count c))))
  (let [v (with-meta (->clj #js [1]) {:foo true})
        _ (count v)
        c (clone v)]
    (is (= v c))
    (is (not (identical? v c)))
    (is (= {:foo true} (meta c)))
    (is (== 1 (count c)))))

(deftest vec-meta-test
  (let [v (->clj #js [:a 1])]
    (count v)
    (is (= {:foo true} (meta (with-meta v {:foo true}))))
    (is (== 2 (count (with-meta v {:foo true})))))
  (let [m {:x 1}]
    (is (= m (meta (assoc (with-meta (->clj #js [1 2]) m) 2 3)))))
  (let [m {:x 1}]
    (is (= m (meta (conj (with-meta (->clj #js [1 2]) m) 3))))))

(deftest vec-peek-test
  (is (nil? (peek (->clj #js []))))
  (is (== 1 (peek (->clj #js [1]))))
  (is (== 2 (peek (->clj #js [1 2]))))
  (is (= [] (peek (->clj #js [1 #js []]))))
  (is (= [1] (peek (->clj #js [1 #js [1]]))))
  (is (= {:a 1} (peek (->clj #js [1 #js {:a 1}]))))
  (is (= [{:a 1}] (peek (->clj #js [1 #js [#js {:a 1}]])))))

(deftest vec-pop-test
  (is (thrown-with-msg? js/Error #"Can't pop empty vector" (pop (->clj #js[]))))
  (is (= [] (pop (->clj #js [1]))))
  (is (= [1] (pop (->clj #js [1 2]))))
  (is (= [[]] (pop (->clj #js [#js [] 2]))))
  (is (= [{:a 1}] (pop (->clj #js [#js {:a 1} 2]))))
  (is (= [[]] (pop (->clj #js [#js [] #js []])))))

(deftest vec-conj-test
  (is (= [1] (conj (->clj #js []) 1)))
  (is (= [0 1] (conj (->clj #js [0]) 1)))
  (is (= [[]] (conj (->clj #js []) [])))
  (is (not= [[]] (conj (->clj #js []) #js [])))
  (is (= [[]] (conj (->clj #js []) (->clj #js []))))
  (is (= [{:a 1}] (conj (->clj #js []) (->clj #js {:a 1}))))
  (is (js-able? (conj (->clj #js [1]) (bean #js {:a 1}))))
  (is (not (js-able? (conj (->clj #js [1]) {:a 1})))))

(deftest vec-empty-test
  (is (= [] (empty (->clj #js [1]))))
  (is (zero? (count (empty (->clj #js [1])))))
  (is (empty? (empty (->clj #js [1]))))
  (let [m (with-meta (->clj #js [1]) {:foo true})]
    (= {:foo true} (meta (empty m))))
  (is (= [1] (assoc (empty (->clj #js [])) 0 1))))

(deftest vec-equiv-test
  (is (-equiv (->clj #js []) []))
  (is (-equiv (->clj #js []) (->clj #js [])))
  (is (-equiv (->clj #js [:a 1]) [:a 1]))
  (is (-equiv (->clj #js [:a 1]) (->clj #js [:a 1])))
  (is (-equiv (->clj #js [:a #js {:b 2}]) [:a {:b 2}]))
  (is (-equiv (->clj #js [:a #js {:b 2}]) (->clj #js [:a #js {:b 2}]))))

(deftest vec-hash-test
  (is (== (hash [:a 1]) (hash (->clj #js [:a 1]))))
  (is (== (hash [:a {:b 2}]) (hash (->clj #js [:a #js {:b 2}])))))

(deftest vec-seq-test
  (is (nil? (seq (->clj #js []))))
  (is (= [1] (seq (->clj #js [1]))))
  (is (= [] (rest (seq (->clj #js [1])))))
  (is (nil? (next (seq (->clj #js [1])))))
  (is (= [2] (rest (seq (->clj #js [1 2])))))
  (is (= [2] (next (seq (->clj #js [1 2]))))))

(deftest vec-count-test
  (is (zero? (count (->clj #js []))))
  (is (== 1 (count (->clj #js [1]))))
  (is (== 2 (count (->clj #js [1 2])))))

(deftest vec-nth-test
  (is (thrown-with-msg? js/Error #"No item 1 in vector of length 0" (-nth (->clj #js []) 1)))
  (is (thrown-with-msg? js/Error #"No item -1 in vector of length 0" (-nth (->clj #js []) -1)))
  (is (== 1 (-nth (->clj #js [1]) 0)))
  (is (== 2 (-nth (->clj #js [1 2]) 1)))
  (is (= :x (-nth (->clj #js [1]) 1 :x))))

(deftest vec-lookup-test
  (is (== 0 (get (->clj #js [0]) 0)))
  (is (nil? (get (->clj #js [0]) 1)))
  (is (nil? (get (->clj #js [0]) :X)))
  (is (== 0 (get (->clj #js [0]) 0 17)))
  (is (== 17 (get (->clj #js [0]) 1 17))))

(deftest vec-assoc-test
  (is (= [1] (assoc (->clj #js [0]) 0 1)))
  (is (= [0 1] (assoc (->clj #js [0]) 1 1)))
  (is (thrown-with-msg? js/Error #"Index 2 out of bounds  \[0,1\]" (assoc (->clj #js [0]) 2 1)))
  (is (thrown-with-msg? js/Error #"Vector's key for assoc must be a number." (assoc (->clj #js [0]) :k 1)))
  (is (js-able? (assoc (->clj #js [1]) 0 (bean #js {:a 1}))))
  (is (js-able? (assoc (->clj #js [1]) 1 (bean #js {:a 1}))))
  (is (not (js-able? (assoc (->clj #js [1]) 0 {:a 1}))))
  (is (not (js-able? (assoc (->clj #js [1]) 1 {:a 1}))))
  (is (= [{:a 1}] (assoc (->clj #js [0]) 0 {:a 1})))
  (is (= [{:a 1}] (assoc (->clj #js [0]) 0 (->clj #js {:a 1}))))
  (is (= (object? (first (assoc (->clj #js [0]) 0 #js {:a 1}))))))

(deftest vec-contains-key-test
  (is (true? (-contains-key? (->clj #js [1]) 0)))
  (is (false? (-contains-key? (->clj #js [1]) -1)))
  (is (false? (-contains-key? (->clj #js [1]) -1)))
  (is (false? (-contains-key? (->clj #js [1]) :k))))

(deftest vec-find-test
  (is (= [0 :x] (find (->clj #js [:x]) 0)))
  (is (nil? (find (->clj #js [:x]) 1)))
  (is (map-entry? (find (->clj #js [:x]) 0)))
  (is (= [0 {:a 1}] (find (->clj #js [#js {:a 1}]) 0))))

(deftest vec-reduce-test
  (is (== 0 (reduce + (->clj #js []))))
  (is (== 1 (reduce + (->clj #js [1]))))
  (is (== 3 (reduce + (->clj #js [1 2]))))
  (is (== 6 (reduce + (->clj #js [1 2 3]))))
  (is (== 17 (reduce (constantly (reduced 17)) (->clj #js [1 2 3]))))
  (is (== 30 (reduce + 30 (->clj #js []))))
  (is (== 31 (reduce + 30 (->clj #js [1]))))
  (is (== 33 (reduce + 30 (->clj #js [1 2]))))
  (is (== 36 (reduce + 30 (->clj #js [1 2 3])))))

(deftest vec-kv-reduce-test
  (is (= [0 1 1 2 2 3] (reduce-kv (fn [acc k v]
                                    (conj acc k v)) []
                         (->clj #js [1 2 3]))))
  (is (== 17 (reduce-kv (constantly (reduced 17)) []
               (->clj #js [1 2 3])))))

(deftest vec-invoke-test
  (is (== 0 ((->clj #js [0]) 0)))
  (is (thrown-with-msg? js/Error #"No item 12 in vector of length 1"
        ((->clj #js [0]) 12)))
  (is (== 19 ((->clj #js [0]) 12 19)))
  (is (== 0 ((->clj #js [0]) 0 11))))

(deftest vec-reverse-test
  (is (reversible? (->clj #js [17])))
  (is (= [3 2 1] (reverse (->clj #js [1 2 3]))))
  (is (nil? (rseq (->clj #js [])))))

(deftest vec-subvec-test
  (is (= [2 3] (subvec (->clj #js [1 2 3 4]) 1 3)))
  (is (= [2 3 4] (subvec (->clj #js [1 2 3 4]) 1)))
  (is (= [17 2 3] (into [17] (subvec (->clj #js [1 2 3]) 1)))))

(deftest vec-compare-test
  (is (satisfies? IComparable (->clj #js [17 2])))
  (is (= [[3 3] [17 2]] (sort [(->clj #js [17 2]) (->clj #js [3 3])]))))

(deftest vec-iter-test
  (is (iterable? (->clj #js [1])))
  (is (some? (iter (->clj #js [1]))))
  (is (= '[:a] (sequence (->clj #js [:a])))))

(deftest vec-seq-dot-toString-test
  (is (= "(1)" (.toString (seq (->clj #js [1])))))
  (is (= "(1 2)" (.toString (seq (->clj #js [1 2])))))
  (is (= "([])" (.toString (seq (->clj #js [#js []])))))
  (is (= "([1])" (.toString (seq (->clj #js [#js [1]])))))
  (is (= "({:a 1})" (.toString (seq (->clj #js [#js {:a 1}]))))))

(deftest vec-seq-dot-equiv-test
  (is (.equiv (seq (->clj #js [1])) [1]))
  (is (.equiv (seq (->clj #js [1 2])) [1 2]))
  (is (.equiv (seq (->clj #js [#js []])) [[]]))
  (is (.equiv (seq (->clj #js [#js [1]])) [[1]]))
  (is (.equiv (seq (->clj #js [#js {:a 1}])) [{:a 1}])))

(deftest vec-seq-dot-indexOf-test
  (is (zero? (.indexOf (seq (->clj #js [0])) 0)))
  (is (zero? (.indexOf (seq (->clj #js [0])) 0 0)))
  (is (== -1 (.indexOf (seq (->clj #js [0])) 1)))
  (is (== -1 (.indexOf (seq (->clj #js [0])) 1 1)))
  (is (zero? (.indexOf (seq (->clj #js [#js {:a 1}])) {:a 1})))
  (is (zero? (.indexOf (seq (->clj #js [#js {:a 1}])) {:a 1} 0)))
  (is (== -1 (.indexOf (seq (->clj #js [#js {:a 1}])) {:a 2})))
  (is (== -1 (.indexOf (seq (->clj #js [#js {:a 1}])) {:a 2} 1)))
  (is (== -1 (.indexOf (seq (->clj #js [#js {:a 1}])) {:a 1} 1))))

(deftest vec-seq-dot-lastIndexOf-test
  (is (zero? (.lastIndexOf (seq (->clj #js [0])) 0)))
  (is (zero? (.lastIndexOf (seq (->clj #js [0])) 0 1)))
  (is (== -1 (.lastIndexOf (seq (->clj #js [0])) 1)))
  (is (== -1 (.lastIndexOf (seq (->clj #js [0])) 1 1)))
  (is (zero? (.lastIndexOf (seq (->clj #js [#js {:a 1}])) {:a 1})))
  (is (zero? (.lastIndexOf (seq (->clj #js [#js {:a 1}])) {:a 1} 0)))
  (is (== -1 (.lastIndexOf (seq (->clj #js [#js {:a 1}])) {:a 2})))
  (is (== -1 (.lastIndexOf (seq (->clj #js [#js {:a 1}])) {:a 2} 1)))
  (is (zero? (.lastIndexOf (seq (->clj #js [#js {:a 1}])) {:a 1} 1))))

(deftest vec-seq-clone-test
  (let [s (seq (->clj #js [1]))
        _ (count s)
        c (clone s)]
    (is (= s c))
    (is (not (identical? s c)))
    (is (nil? (meta c)))
    (is (== 1 (count c))))
  (let [s (with-meta (seq (->clj #js [1])) {:foo true})
        _ (count s)
        c (clone s)]
    (is (= s c))
    (is (not (identical? s c)))
    (is (= {:foo true} (meta c)))
    (is (== 1 (count c)))))

(deftest vec-seq-meta-test
  (let [s (seq (->clj #js [:a 1]))]
    (count s)
    (is (= {:foo true} (meta (with-meta s {:foo true}))))
    (is (== 2 (count (with-meta s {:foo true})))))
  (let [m {:x 1}]
    (is (nil? (meta (conj (with-meta (seq (->clj #js [1 2])) m) 3))))))

(deftest vec-seq-seq-test
  (is (seqable? (seq (->clj #js [1]))))
  (is (= [1] (seq (seq (->clj #js [1]))))))

(deftest vec-seq-count-test
  (is (counted? (seq (->clj #js [1]))))
  (is (== 1 (count (seq (->clj #js [1])))))
  (is (== 2 (count (seq (->clj #js [1 2])))))
  (is (counted? (rest (seq (->clj #js [1 2])))))
  (is (== 1 (count (rest (seq (->clj #js [1 2])))))))

(deftest vec-seq-nth-test
  (is (= 1 (nth (seq (->clj #js [1 2])) 0)))
  (is (= {:a 1} (nth (seq (->clj #js [#js {:a 1} 2])) 0)))
  (is (thrown-with-msg? js/Error #"Index out of bounds" (nth (seq (->clj #js [1])) 1)))
  (is (= ::not-found (nth (seq (->clj #js [1])) 1 ::not-found))))

(deftest vec-seq-equiv-test
  (is (-equiv (seq (->clj #js [1])) [1]))
  (is (-equiv (seq (->clj #js [#js {:a 1}])) [{:a 1}])))

(deftest vec-seq-conj-test
  (is (= [1] (conj (seq (->clj #js [])) 1)))
  (is (= [:x] (conj (seq (->clj #js [])) :x)))
  (is (= [{:a 1}] (conj (seq (->clj #js [])) (->clj #js {:a 1}))))
  (is (= [:x 1] (conj (seq (->clj #js [1])) :x))))

(deftest vec-seq-empty-test
  (is (= [] (empty (seq (->clj #js [1]))))))

(deftest vec-seq-reduce-test
  (is (== 3 (reduce + (seq (->clj #js [1 2])))))
  (is (== 14 (reduce + (rest (->clj #js [1 2 3 4 5])))))
  (is (== 114 (reduce + 100 (rest (->clj #js [1 2 3 4 5])))))
  (is (== :empty (reduce (fn []
                           :empty)
                   (seq (->clj #js [])))))
  (is (== 7 (reduce (fn [r e]
                      (reduced 7))
              (seq (->clj #js [1 2])))))
  (is (== 3 (reduce +
              0
              (seq (->clj #js [1 2])))))
  (is (== 0 (reduce +
              0
              (seq (->clj #js [])))))
  (is (== 7 (reduce (fn [r e]
                      (reduced 7))
              0
              (seq (bean #js [1 2]))))))

(deftest vec-seq-hash-test
  (is (== (hash (seq [1])) (hash (seq (->clj #js [1])))))
  (is (== (hash (seq [:a {:b 2}])) (hash (seq (->clj #js [:a #js {:b 2}]))))))

(deftest coll-iter-vec-seq-match
  (is (core-test/seq-iter-match (->clj #js [])))
  (is (core-test/seq-iter-match (->clj #js [1])))
  (is (core-test/seq-iter-match (->clj #js [:a #js {:b 2}])))
  (is (core-test/seq-iter-match (->clj #js [1 2]))))

(deftest vec-keywordize-keys-false
  (is (= {"a" 1, "b" [{"c" 2}]}
        (bean #js {:a 1 :b #js [#js {:c 2}]} :recursive true :keywordize-keys false))))

(deftest vec-transient-test
  (is (= [1] (persistent! (transient (->clj #js [1])))))
  (let [t (transient (->clj #js []))]
    (persistent! t)
    (is (thrown-with-msg? js/Error #"persistent! called twice" (persistent! t))))
  (is (= [0 1 2] (into (->clj #js []) (range 3))))
  (is (= [17 0 1 2] (into (->clj #js [17]) (range 3)))))

(deftest vec-transient-count-test
  (is (counted? (transient (->clj #js []))))
  (is (== 0 (count (transient (->clj #js [])))))
  (is (== 1 (count (transient (->clj #js [1])))))
  (is (== 2 (count (transient (->clj #js [1 2])))))
  (is (== 1 (count (assoc! (transient (->clj #js [])) 0 1))))
  (is (== 1 (count (persistent! (-> (transient (->clj #js [])) (assoc! 0 1)))))))

(deftest vec-assoc!-test
  (is (= [1] (persistent! (assoc! (transient (->clj #js [])) 0 1))))
  (is (= [1] (persistent! (assoc! (transient (->clj #js [7])) 0 1))))
  (is (= [1 2] (persistent! (assoc! (transient (->clj #js [1])) 1 2))))
  (is (= [1 {:a 1}] (persistent! (assoc! (transient (->clj #js [1])) 1 (bean #js {:a 1})))))
  (is (js-able? (persistent! (assoc! (transient (->clj #js [1])) 0 (bean #js {:a 1})))))
  (is (js-able? (persistent! (assoc! (transient (->clj #js [1])) 1 (bean #js {:a 1})))))
  (is (not (js-able? (persistent! (assoc! (transient (->clj #js [1])) 0 {:a 1})))))
  (is (not (js-able? (persistent! (assoc! (transient (->clj #js [1])) 1 {:a 1})))))
  (let [t (doto (assoc! (transient (->clj #js [])) 0 1) persistent!)]
    (is (thrown-with-msg? js/Error #"assoc! after persistent!" (assoc! t 0 1)))))

(deftest vec-pop!-test
  (is (= [] (persistent! (pop! (transient (->clj #js [1]))))))
  (is (= [1] (persistent! (pop! (transient (->clj #js [1 2]))))))
  (is (thrown-with-msg? js/Error #"Can't pop empty vector" (pop! (transient (->clj #js [])))))
  (is (thrown-with-msg? js/Error #"pop! after persistent!"
        (let [t (transient (->clj #js [1 2]))]
          (pop! t)
          (persistent! t)
          (pop! t)))))

(deftest vec-conj!-test
  (is (= [1] (persistent! (conj! (transient (->clj #js [])) 1))))
  (is (= [{:a 1}] (persistent! (conj! (transient (->clj #js [])) (bean #js {:a 1})))))
  (is (instance? @#'cljs-bean.core/ArrayVector
        (persistent! (conj! (transient (->clj #js [])) (bean #js {:a 1})))))
  (let [t (doto (conj! (transient (->clj #js [])) 1) persistent!)]
    (is (thrown-with-msg? js/Error #"conj! after persistent!" (conj! t 1)))))

(deftest vec-transient-lookup-test
  (is (== 1 (get (assoc! (transient (->clj #js [])) 0 1) 0)))
  (let [t (assoc! (transient (->clj #js [])) 0 1)]
    (persistent! t)
    (is (thrown-with-msg? js/Error #"nth after persistent!" (get t 0))))
  (is (= :not-found (-lookup (assoc! (transient (->clj #js [])) 0 1) 17 :not-found)))
  ;; See CLJS-3124
  #_(let [t (assoc! (transient (->clj #js [])) 0 1)]
    (persistent! t)
    (is (thrown-with-msg? js/Error #"nth after persistent!" (-lookup t 17 :not-found)))))

(deftest vec-transient-invoke-test
  (is (== 1 ((assoc! (transient (->clj #js [])) 0 1) 0)))
  (is (= :not-found ((assoc! (transient (->clj #js [])) 0 1) :b :not-found))))

(defn expected-js-able? [expected actual]
  (and (= expected actual) (js-able? actual)))

(defn expected-non-js-able? [expected actual]
  (and (= expected actual) (not (js-able? actual))))

(deftest nested-update-test
  (testing "map"
    (is (expected-js-able? {:a 1, :b 2}
          (-> #js {:a 1} ->clj (assoc :b 2))))
    (is (expected-js-able? {:b 2}
          (-> #js {:a 1} ->clj empty (assoc :b 2))))
    (is (expected-js-able? {:a 1, :b {:c 3}}
          (-> #js {:a 1} ->clj (assoc :b (->clj #js {:c 3})))))
    (is (expected-non-js-able? {:a 1, :b {:c 3}}
          (-> #js {:a 1} ->clj (assoc :b {:c 3}))))
    (is (expected-js-able? {:a 1, :b [1]}
          (-> #js {:a 1} ->clj (assoc :b (->clj #js [1])))))
    (is (expected-non-js-able? {:a 1, :b [1]}
          (-> #js {:a 1} ->clj (assoc :b [1]))))
    (is (expected-js-able? {:a 1}
          (-> #js {:a 1, :b 2} ->clj (dissoc :b))))
    (is (expected-js-able? {:a 1, :b 2}
          (-> #js {:a 1} ->clj (conj [:b 2]))))
    (is (expected-js-able? {:a 1, :b {:c 3}}
          (-> #js {:a 1} ->clj (conj [:b (->clj #js {:c 3})]))))
    (is (expected-non-js-able? {:a 1, :b {:c 3}}
          (-> #js {:a 1} ->clj (conj [:b {:c 3}]))))
    (is (expected-js-able? {:a 2}
          (-> #js {:a 1} ->clj (update :a inc))))
    (is (expected-js-able? {:a {:b 2}}
          (-> #js {:a 1} ->clj (update :a (constantly (->clj #js {:b 2}))))))
    (is (expected-non-js-able? {:a {:b 2}}
          (-> #js {:a 1} ->clj (update :a (constantly {:b 2})))))
    (is (expected-js-able? {:a 2}
          (-> #js {:a 1} ->clj (update-in [:a] inc)))))
  (testing "vector"
    (is (expected-js-able? [2]
          (-> #js [1] ->clj (assoc 0 2))))
    (is (expected-js-able? [1 2]
          (-> #js [1] ->clj (assoc 1 2))))
    (is (expected-js-able? [{:c 3}]
          (-> #js [1] ->clj (assoc 0 (->clj #js {:c 3})))))
    (is (expected-js-able? [1 {:c 3}]
          (-> #js [1] ->clj (assoc 1 (->clj #js {:c 3})))))
    (is (expected-non-js-able? [{:c 3}]
          (-> #js [1] ->clj (assoc 0 {:c 3}))))
    (is (expected-js-able? [[1]]
          (-> #js [1] ->clj (assoc 0 (->clj #js [1])))))
    (is (expected-non-js-able? [[1]]
          (-> #js [1] ->clj (assoc 0 [1]))))
    (is (expected-js-able? [1 [1]]
          (-> #js [1] ->clj (assoc 1 (->clj #js [1])))))
    (is (expected-non-js-able? [1 [1]]
          (-> #js [1] ->clj (assoc 1 [1]))))
    (is (expected-js-able? [1]
          (-> #js [1 2] ->clj pop)))
    (is (expected-js-able? [1 2 3]
          (-> #js [1] ->clj (conj 2 3))))
    (is (expected-js-able? [1 {:c 3}]
          (-> #js [1] ->clj (conj (->clj #js {:c 3})))))
    (is (expected-non-js-able? [1 {:c 3}]
          (-> #js [1] ->clj (conj {:c 3}))))
    (is (expected-js-able? [2]
          (-> #js [1] ->clj (update 0 inc))))
    (is (expected-js-able? [1 {:b 2}]
          (-> #js [1] ->clj (update 1 (constantly (->clj #js {:b 2}))))))
    (is (expected-non-js-able? [1 {:b 2}]
          (-> #js [1] ->clj (update 1 (constantly {:b 2})))))
    (is (expected-non-js-able? [{:b 2}]
          (-> #js [1] ->clj (update 0 (constantly {:b 2})))))
    (is (expected-non-js-able? [{:b 2}]
          (-> #js [1] ->clj (update-in [0] (constantly {:b 2}))))))
  (testing "vector in map"
    (is (expected-js-able? {:a [1]}
          (-> #js {:a #js []} ->clj (update-in [:a] conj 1))))
    (is (expected-js-able? {:a [1]}
          (-> #js {:a #js [1 2]} ->clj (update-in [:a] pop))))
    (is (expected-js-able? {:a [1]}
          (-> #js {:a #js []} ->clj (update-in [:a] assoc 0 1))))
    (is (expected-js-able? {:a [1]}
          (-> #js {:a #js [0]} ->clj (update-in [:a 0] inc)))))
  (testing "map in vector"
    (is (expected-js-able? [{:a 1}]
          (-> #js [1] ->clj (update-in [0] (constantly (->clj #js {:a 1}))))))
    (is (expected-js-able? [{:a 1}]
          (-> #js [#js {:a 0}] ->clj (update-in [0 :a] inc))))
    (is (expected-js-able? [{:a 0, :b 1}]
          (-> #js [#js {:a 0}] ->clj (assoc-in [0 :b] 1)))))
  (testing "map in map"
    (is (expected-js-able? {:a {:b 2}}
          (-> #js {:a #js {:b 1}} ->clj (assoc-in [:a :b] 2))))
    (is (expected-js-able? {:a {:b 1, :c 3}}
          (-> #js {:a #js {:b 1}} ->clj (assoc-in [:a :c] 3))))
    (is (expected-js-able? {:a {:b 2}}
          (-> #js {:a #js {:b 1}} ->clj (update-in [:a :b] inc)))))
  (testing "vector in vector"
    (is (expected-js-able? [[2]]
          (-> #js [#js [1]] ->clj (assoc-in [0 0] 2))))
    (is (expected-js-able? [[1 3]]
          (-> #js [#js [1]]  ->clj (assoc-in [0 1] 3))))
    (is (expected-js-able? [[2]]
          (-> #js [#js [1]] ->clj (update-in [0 0] inc))))
    (is (expected-js-able? [[1]]
          (-> #js [#js [1 2]] ->clj (update-in [0] pop)))))
  (testing "map in map in map"
    (is (expected-js-able? {:a {:b {:c 3}}}
          (-> #js {:a #js {:b #js {:c 0}}} ->clj (assoc-in [:a :b :c] 3))))
    (is (expected-js-able? {:a {:b {:c 0 :d 4}}}
          (-> #js {:a #js {:b #js {:c 0}}} ->clj (update-in [:a :b] assoc :d 4))))
    (is (expected-non-js-able? {:a {:b {:c 3}}}
          (-> #js {} ->clj (assoc-in [:a :b :c] 3)))))
  (testing "vector in vector in vector"
    (is (expected-js-able? [[[3]]]
          (-> #js [#js [#js [0]]] ->clj (assoc-in [0 0 0] 3))))
    (is (expected-js-able? [[[0 4]]]
          (-> #js [#js [#js [0]]] ->clj (update-in [0 0] assoc 1 4))))))

(deftest ->clj-test
  (is (nil? (->clj nil)))
  (is (true? (->clj true)))
  (is (== 1 (->clj 1)))
  (is (= "a" (->clj "a")))
  (is (fn? (->clj (fn []))))
  (is (vector? (->clj #js [1])))
  (is (recursive-bean? (->clj #js {:a 1}))))

(defrecord Foo [x])

(defn foo-transform [x _ctx]
  (when (instance? Foo x)
    (:x x)))

(deftest ->clj-keyword-conversion-control-test
  (let [js #js [#js {:a 1, "a/b" 2, "d" 3 "v" #js [#js {:c 2 "d" 4 "x/y" 7}]}]]
    (is (= [{:a 1, :a/b 2, :d 3, :v [{:c 2, :d 4, :x/y 7}]}]
           (->clj js)))
    (is (= 7 (get-in (->clj js) [0 :v 0 :x/y])))
    (is (= [{"a" 1, "a/b" 2, "d" 3, "v" [{"c" 2, "d" 4, "x/y" 7}]}]
           (->clj js :keywordize-keys false)))
    (is (= 7 (get-in (->clj js :keywordize-keys false) [0 "v" 0 "x/y"])))
    (is (= [{:a 1, "a/b" 2, :d 3, :v [{:c 2, :d 4, "x/y" 7}]}]
           (->clj js :prop->key prop->key :key->prop key->prop)))
    (is (= 7 (get-in (->clj js :prop->key prop->key :key->prop key->prop) [0 :v 0 "x/y"])))))

(deftest ->clj-transform-test
  (is (= [{:foo "a", :num 3}]
         (->clj #js [#js {:foo (->Foo "a"), :num 3}] :transform foo-transform))))

(deftest ->js-keyword-conversion-control-test
  (let [clj {:a/b 1}]
    (is (= clj (->clj (->js clj))))
    (is (= {:b 1} (->clj (->js clj :key->prop name))))))

(defspec roundtrip-1
  100
  (prop/for-all [j (gen/fmap ->js gen/any-equatable)]
    (let [c (->clj j)]
      (= c (-> c ->js ->clj)))))

(defspec roundtrip-2
  100
  (prop/for-all [j (gen/fmap ->js gen/any-equatable)]
    (let [c (js->clj j :keywordize-keys true)]
      (= c (-> c ->js ->clj)))))

(defspec roundtrip-3
  100
  (prop/for-all [j (gen/fmap ->js gen/any-equatable)]
    (let [c (->clj j)]
      (= c (-> c ->js ->clj ->js ->clj)))))

(defspec roundtrip-4
  100
  (prop/for-all [j (gen/fmap ->js gen/any-equatable)]
    (let [c (js->clj j :keywordize-keys true)]
      (= c (-> c ->js ->clj ->js ->clj)))))

(defspec roundtrip-5
  100
  (prop/for-all [j (gen/fmap ->js gen/any-equatable)]
    (or (not (object? j))
      (let [c (bean j)]
        (= c (-> c ->js bean))))))

(deftest issue-38-test
  (let [b (bean #js {:a 1})]
    (is (== 1 (count b)))
    (is (== 1 (count (dissoc b :b))))
    (is (== 0 (count (dissoc b :a)))))
  (let [t (transient (bean #js {:a 1}))]
    (is (== 1 (count t)))
    (let [t' (dissoc! t :b)]
      (is (== 1 (count t')))
      (is (== 0 (count (dissoc! t' :a)))))))

(deftest issue-68-test
  (is (not (bean? (assoc (->clj #js {}) :b {:x 2}))))
  (is (not (bean? (assoc (->clj #js {}) :b [1 2 3]))))
  (is (not (bean? (persistent! (assoc! (transient (->clj #js {})) :b {:x 2})))))
  (is (not (bean? (persistent! (assoc! (transient (->clj #js {})) :b [1 2 3])))))
  (is (not (array-vector? (conj (->clj #js []) {:x 2}))))
  (is (not (array-vector? (assoc (->clj #js [1]) 0 {:x 2}))))
  (is (not (array-vector? (persistent! (conj! (transient (->clj #js [])) {:x 2})))))
  (is (not (array-vector? (persistent! (assoc! (transient (->clj #js [1])) 0 {:x 2}))))))

(deftest issue-67-test
  (is (= {:foo "a", :num 3}
        (bean #js {:foo (->Foo "a"), :num 3}
          :recursive true
          :transform foo-transform))))

(deftest issue-86-test
  (let [clj-obj (->clj #js {:coll #js [#js {:id "foo"}]})]
    (is (= (conj (:coll clj-obj) {:id "bar"})
          [{:id "foo"} {:id "bar"}])))
  (is (expected-js-able? [{:a 1} 1] (conj (->clj #js [#js {:a 1}]) 1)))
  (is (expected-non-js-able? [{:a 1} {:b 2}] (conj (->clj #js [#js {:a 1}]) {:b 2})))
  (is (expected-js-able? [{:a 1} 1] (persistent! (conj! (transient (->clj #js [#js {:a 1}])) 1))))
  (is (expected-non-js-able? [{:a 1} {:b 2}] (persistent! (conj! (transient (->clj #js [#js {:a 1}])) {:b 2}))))
  (is (expected-js-able? [{:a 1} 1] (assoc (->clj #js [#js {:a 1}]) 1 1)))
  (is (expected-non-js-able? [{:a 1} {:b 2}] (assoc (->clj #js [#js {:a 1}]) 1 {:b 2})))
  (is (expected-js-able? [{:a 1} 1] (persistent! (assoc! (transient (->clj #js [#js {:a 1}])) 1 1))))
  (is (expected-non-js-able? [{:a 1} {:b 2}] (persistent! (assoc! (transient (->clj #js [#js {:a 1}])) 1 {:b 2})))))
