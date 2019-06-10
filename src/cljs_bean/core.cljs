(ns cljs-bean.core
  (:require
   [goog.object :as gobj]))

(def ^:private lookup-sentinel #js {})

(defn- prop->key [prop]
  (let [matches  (.exec #"[A-Za-z_\*\+\?!\-'][\w\*\+\?!\-']*" prop)
        matches? (and (== 1 (alength matches))
                   (identical? prop (aget matches 0)))]
    (cond-> prop matches? keyword)))

(defn- key->prop [key]
  (cond
    (simple-keyword? key) (name key)
    (and (string? key)
      (string? (prop->key key))) key
    :else nil))

(defn- snapshot [x]
  (let [result (volatile! (transient {}))]
    (gobj/forEach x (fn [v k _] (vswap! result assoc! (prop->key k) v)))
    (persistent! @result)))

(deftype ^:private Bean [meta obj ^:mutable __hash]
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
  (-clone [_] (Bean. meta obj __hash))

  IWithMeta
  (-with-meta [coll new-meta]
    (if (identical? new-meta meta)
      coll
      (Bean. new-meta obj __hash)))

  IMeta
  (-meta [_] meta)

  ICollection
  (-conj [_ entry]
    (-conj (snapshot obj) entry))

  IEmptyableCollection
  (-empty [_] (-with-meta {} meta))

  IEquiv
  (-equiv [coll other]
    (equiv-map coll other))

  IHash
  (-hash [coll] (caching-hash coll hash-unordered-coll __hash))

  IIterable
  (-iterator [_]
    (-iterator (snapshot obj)))

  ISeqable
  (-seq [_]
    (-seq (snapshot obj)))

  IAssociative
  (-assoc [_ k v]
    (-assoc (snapshot obj) k v))

  (-contains-key? [coll k]
    (contains? coll k))

  IFind
  (-find [coll k]
    (let [v (-lookup coll k lookup-sentinel)]
      (when-not (identical? v lookup-sentinel)
        (->MapEntry k v nil))))

  IMap
  (-dissoc [_ k]
    (-dissoc (snapshot obj) k))

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
    (-reduce (snapshot obj) f))
  (-reduce [_ f start]
    (-reduce (snapshot obj) f start))

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
  [x]
  (Bean. nil x nil))
