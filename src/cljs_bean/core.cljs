(ns cljs-bean.core
  (:require
   [goog.object :as gobj]))

(def ^:private lookup-sentinel #js {})

(defn- snapshot [x prop->key]
  (let [result (volatile! (transient {}))]
    (gobj/forEach x (fn [v k _] (vswap! result assoc! (prop->key k) v)))
    (persistent! @result)))

(defn- thisfn [obj ks prop->key]
  (when-let [ks (seq ks)]
    (let [first-ks (first ks)]
      (lazy-seq
        (cons (MapEntry. (prop->key first-ks) (unchecked-get obj first-ks) nil)
          (thisfn obj (rest ks) prop->key))))))

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
    (thisfn obj (js-keys obj) prop->key))

  IAssociative
  (-assoc [_ k v]
    (-assoc (snapshot obj prop->key) k v))

  (-contains-key? [coll k]
    (contains? coll k))

  IFind
  (-find [coll k]
    (let [v (-lookup coll k lookup-sentinel)]
      (when-not (identical? v lookup-sentinel)
        (->MapEntry k v nil))))

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
  (-reduce [_ f]
    (-reduce (snapshot obj prop->key) f))
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
