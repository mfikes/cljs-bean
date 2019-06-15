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

(defn- compatible-key? [k prop->key]
  (or
    (and (keyword? k) (identical? prop->key keyword))
    (and (string? k) (identical? prop->key identity))))

(declare Bean)

(deftype TransientBean [^:mutable ^boolean editable?
                        obj prop->key key->prop
                        ^:mutable __cnt]
  ILookup
  (-lookup [_ k]
    (if editable?
      (unchecked-get obj (key->prop k))
      (throw (js/Error. "lookup after persistent!"))))
  (-lookup [_ k not-found]
    (if editable?
      (gobj/get obj (key->prop k) not-found)
      (throw (js/Error. "lookup after persistent!"))))

  ICounted
  (-count [_]
    (if (nil? __cnt)
      (set! __cnt (count (js-keys obj)))
      __cnt))

  ITransientCollection
  (-conj! [tcoll o]
    (if editable?
      (cond
        (map-entry? o)
        (-assoc! tcoll (key o) (val o))

        (vector? o)
        (-assoc! tcoll (o 0) (o 1))

        :else
        (loop [es (seq o) tcoll tcoll]
          (if-let [e (first es)]
            (recur (next es)
              (-assoc! tcoll (key e) (val e)))
            tcoll)))
      (throw (js/Error. "conj! after persistent!"))))

  (-persistent! [tcoll]
    (if editable?
      (do
        (set! editable? false)
        (Bean. nil obj prop->key key->prop __cnt nil))
      (throw (js/Error. "persistent! called twice"))))

  ITransientAssociative
  (-assoc! [tcoll k v]
    (if editable?
      (if (compatible-key? k prop->key)
        (do
          (unchecked-set obj (key->prop k) v)
          (set! __cnt nil)
          tcoll)
        (-assoc! (transient (snapshot obj prop->key)) k v))
      (throw (js/Error. "assoc! after persistent!"))))

  ITransientMap
  (-dissoc! [tcoll k]
    (if editable?
      (let [deleted? (js-delete obj (key->prop k))]
        (when (and deleted? __cnt)
          (set! __cnt (dec __cnt)))
        tcoll)
      (throw (js/Error. "dissoc! after persistent!"))))

  IFn
  (-invoke [tcoll key]
    (-lookup tcoll key nil))
  (-invoke [tcoll key not-found]
    (-lookup tcoll key not-found)))

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

(declare ^{:arglists '([x])} bean?)
(declare ^{:arglists '([bean])} object)

(deftype ^:private Bean [meta obj prop->key key->prop ^:mutable __cnt ^:mutable __hash]
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
  (-clone [_] (Bean. meta obj prop->key key->prop __cnt __hash))

  IWithMeta
  (-with-meta [coll new-meta]
    (if (identical? new-meta meta)
      coll
      (Bean. new-meta obj prop->key key->prop __cnt __hash)))

  IMeta
  (-meta [_] meta)

  ICollection
  (-conj [coll entry]
    (if (vector? entry)
      (-assoc coll (entry 0) (entry 1))
      (loop [ret coll es (seq entry)]
        (if (nil? es)
          ret
          (let [e (first es)]
            (if (vector? e)
              (recur (-assoc ret (e 0) (e 1)) (next es))
              (throw (js/Error. "conj on a map takes map entries or seqables of map entries"))))))))

  IEmptyableCollection
  (-empty [_] (Bean. meta #js {} prop->key key->prop 0 nil))

  IEquiv
  (-equiv [coll other]
    (equiv-map coll other))

  IHash
  (-hash [coll] (caching-hash coll hash-unordered-coll __hash))

  ISeqable
  (-seq [_]
    (let [props (js-keys obj)]
      (when (pos? (alength props))
        (BeanSeq. obj prop->key props 0 nil))))

  IAssociative
  (-assoc [_ k v]
    (if (compatible-key? k prop->key)
      (Bean. nil
        (doto (gobj/clone obj) (unchecked-set (key->prop k) v))
        prop->key key->prop nil nil)
      (-assoc (snapshot obj prop->key) k v)))

  (-contains-key? [coll k]
    (contains? coll k))

  IFind
  (-find [coll k]
    (let [v (-lookup coll k lookup-sentinel)]
      (when-not (identical? v lookup-sentinel)
        (MapEntry. k v nil))))

  IMap
  (-dissoc [_ k]
    (let [obj'     (gobj/clone obj)
          deleted? (js-delete obj' (key->prop k))
          cnt' (when (and __cnt deleted?)
                 (dec __cnt))]
      (Bean. nil obj' prop->key key->prop cnt' nil)))

  ICounted
  (-count [_]
    (if (nil? __cnt)
      (set! __cnt (count (js-keys obj)))
      __cnt))

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
    (TransientBean. true (gobj/clone obj) prop->key key->prop __cnt))

  IPrintWithWriter
  (-pr-writer [coll writer opts]
    (print-map coll pr-writer writer opts)))

(defn bean
  "Takes a JavaScript object and returns a read-only implementation of the map
  abstraction backed by the object. By default, bean produces beans that
  keywordize the keys. Supply :keywordize-keys false to suppress this behavior.
  You can alternatively supply :prop->key and :key->prop with functions that
  controls the mapping between properties and keys. Calling (bean) produces an
  empty bean."
  ([]
   (Bean. nil #js {} keyword #(.-fqn %) 0 nil))
  ([x]
   (Bean. nil x keyword #(.-fqn %) nil nil))
  ([x & opts]
   (let [{:keys [keywordize-keys prop->key key->prop]} opts]
     (cond
       (false? keywordize-keys) (Bean. nil x identity identity nil nil)
       (true? keywordize-keys) (bean x)
       (and (some? prop->key) (some? key->prop)) (Bean. nil x prop->key key->prop nil nil)
       :else (throw (js/Error ":keywordize-keys or both :prop->key and :key->prop must be specified"))))))

(defn bean?
  "Returns true if x is a bean."
  [x]
  (instance? Bean x))

(defn object
  "Takes a bean and returns a JavaScript object."
  [bean]
  (.-obj bean))
