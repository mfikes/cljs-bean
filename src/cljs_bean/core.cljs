(ns cljs-bean.core
  (:require
   [goog.object :as gobj]))

(def ^:private lookup-sentinel #js {})

(defn- snapshot [x prop->key]
  (let [result (volatile! (transient {}))]
    (gobj/forEach x (fn [v k _] (vswap! result assoc! (prop->key k) v)))
    (persistent! @result)))

(defn- bean-seq-entry [obj prop->key arr i]
  (let [prop (aget arr i)]
    (MapEntry. (prop->key prop) (unchecked-get obj prop) nil)))

(deftype ^:private BeanSeq [obj prop->key arr i meta]
  Object
  (toString [coll]
    (pr-str* coll))
  (equiv [this other]
    (-equiv this other))
  (indexOf [coll x]
    (-indexOf coll x 0))
  (indexOf [coll x start]
    (-indexOf coll x start))
  (lastIndexOf [coll x]
    (-lastIndexOf coll x (count coll)))
  (lastIndexOf [coll x start]
    (-lastIndexOf coll x start))

  ICloneable
  (-clone [_] (BeanSeq. obj prop->key arr i meta))

  ISeqable
  (-seq [this]
    (when (< i (alength arr))
      this))

  IMeta
  (-meta [_] meta)
  IWithMeta
  (-with-meta [coll new-meta]
    (if (identical? new-meta meta)
      coll
      (BeanSeq. obj prop->key arr i new-meta)))

  ASeq
  ISeq
  (-first [_] (bean-seq-entry obj prop->key arr i))
  (-rest [_] (if (< (inc i) (alength arr))
               (BeanSeq. obj prop->key arr (inc i) nil)
               ()))

  INext
  (-next [_] (if (< (inc i) (alength arr))
               (BeanSeq. obj prop->key arr (inc i) nil)
               nil))

  ICounted
  (-count [_]
    (max 0 (- (alength arr) i)))

  IIndexed
  (-nth [_ n]
    (let [i (+ n i)]
      (if (and (<= 0 i) (< i (alength arr)))
        (bean-seq-entry obj prop->key arr i)
        (throw (js/Error. "Index out of bounds")))))
  (-nth [_ n not-found]
    (let [i (+ n i)]
      (if (and (<= 0 i) (< i (alength arr)))
        (bean-seq-entry obj prop->key arr i)
        not-found)))

  ISequential
  IEquiv
  (-equiv [coll other] (equiv-sequential coll other))

  ICollection
  (-conj [coll o] (cons o coll))

  IEmptyableCollection
  (-empty [_] ())

  IReduce
  (-reduce [coll f]
    (ci-reduce coll f (bean-seq-entry obj prop->key arr i) (inc i)))
  (-reduce [coll f start]
    (ci-reduce coll f start i))

  IHash
  (-hash [coll] (hash-ordered-coll coll))

  IPrintWithWriter
  (-pr-writer [coll writer opts]
    (pr-sequential-writer writer pr-writer "(" " " ")" opts coll)))

(deftype ^:private Bean [meta obj prop->key key->prop ^:mutable __hash]
  Object
  (toString [coll]
    (pr-str* coll))
  (equiv [this other]
    (-equiv this other))

  (keys [coll]
    (es6-iterator (keys coll)))
  (entries [coll]
    (es6-entries-iterator (seq coll)))
  (values [coll]
    (es6-iterator (vals coll)))
  (has [coll k]
    (contains? coll k))
  (get [coll k not-found]
    (-lookup coll k not-found))
  (forEach [coll f]
    (doseq [[k v] coll]
      (f v k)))

  ICloneable
  (-clone [_] (Bean. meta obj prop->key key->prop __hash))

  IWithMeta
  (-with-meta [coll new-meta]
    (if (identical? new-meta meta)
      coll
      (Bean. new-meta obj prop->key key->prop __hash)))

  IMeta
  (-meta [_] meta)

  ICollection
  (-conj [_ entry]
    (-conj (snapshot obj prop->key) entry))

  IEmptyableCollection
  (-empty [_] (-with-meta {} meta))

  IEquiv
  (-equiv [coll other]
    (equiv-map coll other))

  IHash
  (-hash [coll] (caching-hash coll hash-unordered-coll __hash))

  IIterable
  (-iterator [_]
    (-iterator (snapshot obj prop->key)))

  ISeqable
  (-seq [_]
    (let [props (js-keys obj)]
      (when (pos? (alength props))
        (BeanSeq. obj prop->key props 0 nil))))

  IAssociative
  (-assoc [_ k v]
    (-assoc (snapshot obj prop->key) k v))

  (-contains-key? [coll k]
    (contains? coll k))

  IFind
  (-find [coll k]
    (let [v (-lookup coll k lookup-sentinel)]
      (when-not (identical? v lookup-sentinel)
        (MapEntry. k v nil))))

  IMap
  (-dissoc [_ k]
    (-dissoc (snapshot obj prop->key) k))

  ILookup
  (-lookup [_ k]
    (unchecked-get obj (key->prop k)))
  (-lookup [_ k not-found]
    (gobj/get obj (key->prop k) not-found))

  IKVReduce
  (-kv-reduce [_ f init]
    (try
      (let [result (volatile! init)]
        (gobj/forEach obj
          (fn [v k _]
            (let [r (vswap! result f (prop->key k) v)]
              (when (reduced? r) (throw r)))))
        @result)
      (catch :default x
        (if (reduced? x) @x (throw x)))))

  IReduce
  (-reduce [coll f]
    (-reduce (-seq coll) f))
  (-reduce [coll f start]
    (-kv-reduce coll (fn [r k v] (f r (MapEntry. k v nil))) start))

  IFn
  (-invoke [coll k]
    (-lookup coll k))

  (-invoke [coll k not-found]
    (-lookup coll k not-found))

  IEditableCollection
  (-as-transient [_]
    (let [result (volatile! (transient {}))]
      (gobj/forEach obj (fn [v k _] (vswap! result assoc! (prop->key k) v)))
      @result))

  IPrintWithWriter
  (-pr-writer [coll writer opts]
    (print-map coll pr-writer writer opts)))

(defn bean
  "Takes a JavaScript object and returns a read-only implementation of the
  map abstraction backed by the object."
  ([x]
   (Bean. nil x keyword #(.-fqn %) nil))
  ([x & opts]
   (let [{:keys [keywordize-keys prop->key key->prop]} opts]
     (cond
       (false? keywordize-keys) (Bean. nil x identity identity nil)
       (true? keywordize-keys) (bean x)
       (and (some? prop->key) (some? key->prop)) (Bean. nil x prop->key key->prop nil)
       :else (throw (js/Error ":keywordize-keys or both :prop->key and :key->prop must be specified"))))))
