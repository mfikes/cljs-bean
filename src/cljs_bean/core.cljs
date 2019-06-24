(ns cljs-bean.core
  (:require
   [goog.object :as gobj]))

(declare Bean)
(declare ArrayVector)
(declare bean?)
(declare object)

(def ^:private lookup-sentinel #js {})

(defn- ->val [x prop->key key->prop]
  (cond
    (number? x) x
    (string? x) x
    (boolean? x) x
    (nil? x) x
    (object? x) (Bean. nil x prop->key key->prop true nil nil nil)
    (array? x) (ArrayVector. nil prop->key key->prop x nil)
    :else x))

(defn- snapshot [x prop->key key->prop recursive?]
  (let [result (volatile! (transient {}))]
    (gobj/forEach x (fn [v k _] (vswap! result assoc! (prop->key k)
                                  (cond-> v
                                    recursive? (->val prop->key key->prop)))))
    (persistent! @result)))

(defn- indexed-entry [obj prop->key key->prop ^boolean recursive? arr i]
  (let [prop (aget arr i)]
    (MapEntry. (prop->key prop)
      (cond-> (unchecked-get obj prop)
        recursive? (->val prop->key key->prop))
      nil)))

(defn- compatible-key? [k prop->key]
  (or
    (and (keyword? k) (identical? prop->key keyword))
    (and (string? k) (identical? prop->key identity))))

(deftype ^:private TransientBean [^:mutable ^boolean editable?
                                  obj prop->key key->prop ^boolean recursive?
                                  ^:mutable __cnt]
  ILookup
  (-lookup [_ k]
    (if editable?
      (cond-> (unchecked-get obj (key->prop k))
        recursive? (->val prop->key key->prop))
      (throw (js/Error. "lookup after persistent!"))))
  (-lookup [_ k not-found]
    (if editable?
      (let [ret (gobj/get obj (key->prop k) not-found)]
        (cond-> ret
          (and recursive? (not (identical? ret not-found)))
          (->val prop->key key->prop)))
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
        (Bean. nil obj prop->key key->prop recursive? nil __cnt nil))
      (throw (js/Error. "persistent! called twice"))))

  ITransientAssociative
  (-assoc! [tcoll k v]
    (if editable?
      (if (and (compatible-key? k prop->key)
               (not (and recursive?
                         (or (object? v)
                             (array? v)))))
        (do
          (unchecked-set obj (key->prop k)
            (cond-> v
              (and recursive? (bean? v)) object
              (and recursive? (instance? ArrayVector v)) .-arr))
          (set! __cnt nil)
          tcoll)
        (-assoc! (transient (snapshot obj prop->key key->prop recursive?)) k v))
      (throw (js/Error. "assoc! after persistent!"))))

  ITransientMap
  (-dissoc! [tcoll k]
    (if editable?
      (do
        (js-delete obj (key->prop k))
        (set! __cnt nil)
        tcoll)
      (throw (js/Error. "dissoc! after persistent!"))))

  IFn
  (-invoke [_ k]
    (if editable?
      (cond-> (unchecked-get obj (key->prop k))
        recursive? (->val prop->key key->prop))
      (throw (js/Error. "lookup after persistent!"))))
  (-invoke [_ k not-found]
    (if editable?
      (let [ret (gobj/get obj (key->prop k) not-found)]
        (cond-> ret
          (and recursive? (not (identical? ret not-found)))
          (->val prop->key key->prop)))
      (throw (js/Error. "lookup after persistent!")))))

(deftype ^:private BeanIterator [obj prop->key key->prop ^boolean recursive? arr ^:mutable i cnt]
  Object
  (hasNext [_]
    (< i cnt))
  (next [_]
    (let [ret (indexed-entry obj prop->key key->prop recursive? arr i)]
      (set! i (inc i))
      ret)))

(deftype ^:private BeanSeq [obj prop->key key->prop ^boolean recursive? arr i meta]
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
  (-clone [_] (BeanSeq. obj prop->key key->prop recursive? arr i meta))

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
      (BeanSeq. obj prop->key key->prop recursive? arr i new-meta)))

  ASeq
  ISeq
  (-first [_] (indexed-entry obj prop->key key->prop recursive? arr i))
  (-rest [_] (if (< (inc i) (alength arr))
               (BeanSeq. obj prop->key key->prop recursive? arr (inc i) nil)
               ()))

  INext
  (-next [_] (if (< (inc i) (alength arr))
               (BeanSeq. obj prop->key key->prop recursive? arr (inc i) nil)
               nil))

  ICounted
  (-count [_]
    (max 0 (- (alength arr) i)))

  IIndexed
  (-nth [_ n]
    (let [i (+ n i)]
      (if (and (<= 0 i) (< i (alength arr)))
        (indexed-entry obj prop->key key->prop recursive? arr i)
        (throw (js/Error. "Index out of bounds")))))
  (-nth [_ n not-found]
    (let [i (+ n i)]
      (if (and (<= 0 i) (< i (alength arr)))
        (indexed-entry obj prop->key key->prop recursive? arr i)
        not-found)))

  ISequential
  IEquiv
  (-equiv [coll other]
    (boolean
      (when (sequential? other)
        (if (and (counted? other) (not (== (-count coll) (-count other))))
          false
          (loop [xs (-seq coll) ys (seq other)]
            (cond (nil? xs) (nil? ys)
                  (nil? ys) false
                  (= (first xs) (first ys)) (recur (next xs) (next ys))
                  :else false))))))

  ICollection
  (-conj [coll o] (cons o coll))

  IEmptyableCollection
  (-empty [_] ())

  IReduce
  (-reduce [coll f]
    (let [cnt (-count coll)]
      (loop [val (indexed-entry obj prop->key key->prop recursive? arr i), n (inc i)]
        (if (< n cnt)
          (let [nval (f val (-nth coll n))]
            (if (reduced? nval)
              @nval
              (recur nval (inc n))))
          val))))
  (-reduce [coll f start]
    (let [cnt (-count coll)]
      (loop [val start, n i]
        (if (< n cnt)
          (let [nval (f val (-nth coll n))]
            (if (reduced? nval)
              @nval
              (recur nval (inc n))))
          val))))

  IHash
  (-hash [coll] (hash-ordered-coll coll))

  IPrintWithWriter
  (-pr-writer [coll writer opts]
    (pr-sequential-writer writer pr-writer "(" " " ")" opts coll)))

(deftype ^:private Bean [meta obj prop->key key->prop ^boolean recursive?
                         ^:mutable __arr ^:mutable __cnt ^:mutable __hash]
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
  (-clone [_] (Bean. meta obj prop->key key->prop recursive? __arr __cnt __hash))

  IWithMeta
  (-with-meta [coll new-meta]
    (if (identical? new-meta meta)
      coll
      (Bean. new-meta obj prop->key key->prop recursive? __arr __cnt __hash)))

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
  (-empty [_] (Bean. meta #js {} prop->key key->prop recursive? #js []  0 nil))

  IEquiv
  (-equiv [coll other]
    (equiv-map coll other))

  IHash
  (-hash [coll] (caching-hash coll hash-unordered-coll __hash))

  IIterable
  (-iterator [coll]
    (when (nil? __arr)
      (set! __arr (js-keys obj)))
    (BeanIterator. obj prop->key key->prop recursive? __arr 0 (-count coll)))

  ISeqable
  (-seq [_]
    (when (nil? __arr)
      (set! __arr (js-keys obj)))
    (when (pos? (alength __arr))
      (BeanSeq. obj prop->key key->prop recursive? __arr 0 nil)))

  IAssociative
  (-assoc [_ k v]
    (if (and (compatible-key? k prop->key)
             (not (and recursive?
                       (or (object? v)
                           (array? v)))))
      (Bean. meta
        (doto (gobj/clone obj) (unchecked-set (key->prop k)
                                 ;; TODO short circuit this
                                 (cond-> v
                                   (and recursive? (bean? v)) object
                                   (and recursive? (instance? ArrayVector v)) .-arr)))
        prop->key key->prop recursive? nil nil nil)
      (-assoc (with-meta (snapshot obj prop->key key->prop recursive?) meta) k v)))

  (-contains-key? [coll k]
    (contains? coll k))

  IFind
  (-find [_ k]
    (let [v (gobj/get obj (key->prop k) lookup-sentinel)]
      (when-not (identical? v lookup-sentinel)
        (MapEntry. k (cond-> v recursive? (->val prop->key key->prop)) nil))))

  IMap
  (-dissoc [_ k]
    (Bean. meta (doto (gobj/clone obj) (js-delete (key->prop k)))
      prop->key key->prop recursive? nil nil nil))

  ICounted
  (-count [_]
    (if (nil? __cnt)
      (do
        (when (nil? __arr)
          (set! __arr (js-keys obj)))
        (set! __cnt (alength __arr)))
      __cnt))

  ILookup
  (-lookup [_ k]
    (cond-> (unchecked-get obj (key->prop k))
      recursive? (->val prop->key key->prop)))
  (-lookup [_ k not-found]
    (let [ret (gobj/get obj (key->prop k) not-found)]
      (cond-> ret
        (and recursive? (not (identical? ret not-found)))
        (->val prop->key key->prop))))

  IKVReduce
  (-kv-reduce [_ f init]
    (try
      (let [result (volatile! init)]
        (gobj/forEach obj
          (fn [v k _]
            (let [r (vswap! result f (prop->key k)
                      (cond-> v recursive? (->val prop->key key->prop)))]
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
  (-invoke [_ k]
    (cond-> (unchecked-get obj (key->prop k))
      recursive? (->val prop->key key->prop)))

  (-invoke [_ k not-found]
    (let [ret (gobj/get obj (key->prop k) not-found)]
      (cond-> ret
        (and recursive? (not (identical? ret not-found)))
        (->val prop->key key->prop))))

  IEditableCollection
  (-as-transient [_]
    (TransientBean. true (gobj/clone obj) prop->key key->prop recursive? __cnt))

  IPrintWithWriter
  (-pr-writer [coll writer opts]
    (print-map coll pr-writer writer opts)))

(deftype ^:private ArrayVectorSeq [prop->key key->prop arr i meta]
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
  (-clone [_] (ArrayVectorSeq. prop->key key->prop arr i meta))

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
      (ArrayVectorSeq. prop->key key->prop arr i new-meta)))

  ASeq
  ISeq
  (-first [_] (->val (aget arr i) prop->key key->prop))
  (-rest [_] (if (< (inc i) (alength arr))
               (ArrayVectorSeq. prop->key key->prop arr (inc i) nil)
               ()))

  INext
  (-next [_] (if (< (inc i) (alength arr))
               (ArrayVectorSeq. prop->key key->prop arr (inc i) nil)
               nil))

  ICounted
  (-count [_]
    (max 0 (- (alength arr) i)))

  IIndexed
  (-nth [_ n]
    (let [i (+ n i)]
      (if (and (<= 0 i) (< i (alength arr)))
        (aget arr i)
        (throw (js/Error. "Index out of bounds")))))
  (-nth [_ n not-found]
    (let [i (+ n i)]
      (if (and (<= 0 i) (< i (alength arr)))
        (aget arr i)
        not-found)))

  ISequential
  IEquiv
  (-equiv [coll other]
    (boolean
      (when (sequential? other)
        (if (and (counted? other) (not (== (-count coll) (-count other))))
          false
          (loop [xs (-seq coll) ys (seq other)]
            (cond (nil? xs) (nil? ys)
                  (nil? ys) false
                  (= (first xs) (first ys)) (recur (next xs) (next ys))
                  :else false))))))

  ICollection
  (-conj [coll o] (cons o coll))

  IEmptyableCollection
  (-empty [_] ())

  IReduce
  (-reduce [coll f]
    (let [cnt (-count coll)]
      (loop [val (-nth coll i), n (inc i)]
        (if (< n cnt)
          (let [nval (f val (-nth coll n))]
            (if (reduced? nval)
              @nval
              (recur nval (inc n))))
          val))))
  (-reduce [coll f start]
    (let [cnt (-count coll)]
      (loop [val start, n i]
        (if (< n cnt)
          (let [nval (f val (-nth coll n))]
            (if (reduced? nval)
              @nval
              (recur nval (inc n))))
          val))))

  IHash
  (-hash [coll] (hash-ordered-coll coll))

  IPrintWithWriter
  (-pr-writer [coll writer opts]
    (pr-sequential-writer writer pr-writer "(" " " ")" opts coll)))

(deftype ^:private ArrayVector [meta prop->key key->prop arr ^:mutable __hash]
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
    (-lastIndexOf coll x (alength coll)))
  (lastIndexOf [coll x start]
    (-lastIndexOf coll x start))

  ICloneable
  (-clone [_] (ArrayVector. meta prop->key key->prop arr __hash))

  IWithMeta
  (-with-meta [coll new-meta]
    (if (identical? new-meta meta)
      coll
      (ArrayVector. new-meta prop->key key->prop arr __hash)))

  IMeta
  (-meta [coll] meta)

  IStack
  (-peek [coll]
    (when (pos? (alength arr))
      (-nth coll (dec (alength arr)))))
  (-pop [coll]
    (cond
        (zero? (alength arr)) (throw (js/Error. "Can't pop empty vector"))
        (== 1 (alength arr)) (-empty coll)
        :else
        (let [new-arr (aclone arr)]
          (ArrayVector. meta prop->key key->prop
            (.slice new-arr 0 (dec (alength new-arr))) nil))))

  ICollection
  (-conj [_ o]
    (if (or (object? o) (array? o))
      (-conj (vec arr) o)
      (let [new-arr (aclone arr)]
        (unchecked-set new-arr (alength new-arr)
          (cond-> o
            (bean? o) object
            (instance? ArrayVector o) .-arr))
        (ArrayVector. meta prop->key key->prop new-arr nil))))

  IEmptyableCollection
  (-empty [coll]
    (ArrayVector. meta prop->key key->prop #js [] nil))

  ISequential
  IEquiv
  (-equiv [coll other]
    (if false #_(instance? ArrayVector other)
      (if (== (alength arr) (count other))
        (let [me-iter  (-iterator coll)
              you-iter (-iterator other)]
          (loop []
            (if ^boolean (.hasNext me-iter)
              (let [x (.next me-iter)
                    y (.next you-iter)]
                (if (= x y)
                  (recur)
                  false))
              true)))
        false)
      (equiv-sequential coll other)))

  IHash
  (-hash [coll] (caching-hash coll hash-ordered-coll __hash))

  ISeqable
  (-seq [coll]
    (when (pos? (alength arr))
      (ArrayVectorSeq. prop->key key->prop arr 0 nil)))

  ICounted
  (-count [coll] (alength arr))

  IIndexed
  (-nth [coll n]
    (->val (aget arr n) prop->key key->prop))
  (-nth [coll n not-found]
    (if (and (<= 0 n) (< n (alength arr)))
      (->val (aget arr n) prop->key key->prop)
      not-found))

  ILookup
  (-lookup [coll k] (-lookup coll k nil))
  (-lookup [coll k not-found] (if (number? k)
                                (-nth coll k not-found)
                                not-found))

  IAssociative
  (-assoc [coll k v]
    (if (number? k)
      (-assoc-n coll k v)
      (throw (js/Error. "Vector's key for assoc must be a number."))))
  (-contains-key? [coll k]
    (if (integer? k)
      (and (<= 0 k) (< k (alength arr)))
      false))

  IFind
  (-find [coll n]
    (when (and (<= 0 n) (< n (alength arr)))
      (MapEntry. n (->val (aget arr n) prop->key key->prop) nil)))

  APersistentVector
  IVector
  (-assoc-n [coll n val]
    (cond
      (and (<= 0 n) (< n (alength arr)))
      (if (or (object? val) (array? val))
        (-assoc-n (vec arr) n val)
        (let [new-arr (aclone arr)]
          (aset new-arr n (cond-> val
                            (bean? val) object
                            (instance? ArrayVector val) .-arr))
          (ArrayVector. meta prop->key key->prop new-arr nil)))
      (== n (alength arr)) (-conj coll val)
      :else (throw (js/Error. (str "Index " n " out of bounds  [0," (alength arr) "]")))))


  IReduce
  (-reduce [v f]
    (if (zero? (alength arr))
      (f)
      (loop [i 1 init (-nth v 0)]
        (if (< i (alength arr))
          (let [len  (alength arr)
                init (loop [j 1 init init]
                       (if (< j len)
                         (let [init (f init (->val (aget arr j) prop->key key->prop))]
                           (if (reduced? init)
                             init
                             (recur (inc j) init)))
                         init))]
            (if (reduced? init)
              @init
              (recur (+ i len) init)))
          init))))
  (-reduce [_ f init]
    (loop [i 0 init init]
      (if (< i (alength arr))
        (let [len  (alength arr)
              init (loop [j 0 init init]
                     (if (< j len)
                       (let [init (f init (->val (aget arr j) prop->key key->prop))]
                         (if (reduced? init)
                           init
                           (recur (inc j) init)))
                       init))]
          (if (reduced? init)
            @init
            (recur (+ i len) init)))
        init)))


  IKVReduce
  (-kv-reduce [v f init]
    (loop [i 0 init init]
      (if (< i (alength arr))
        (let [len  (alength arr)
              init (loop [j 0 init init]
                     (if (< j len)
                       (let [init (f init (+ j i) (->val (aget arr j) prop->key key->prop))]
                         (if (reduced? init)
                           init
                           (recur (inc j) init)))
                       init))]
          (if (reduced? init)
            @init
            (recur (+ i len) init)))
        init)))

  IFn
  (-invoke [coll k]
    (-nth coll k))
  (-invoke [coll k not-found]
    (-nth coll k not-found))

  #_#_
  IEditableCollection
  (-as-transient [coll]
    #_(TransientVector. cnt shift (tv-editable-root root) (tv-editable-tail tail)))

  IReversible
  (-rseq [coll]
    (when (pos? (alength arr))
      (RSeq. coll (dec (alength arr)) nil)))

  #_#_
  IIterable
  (-iterator [this]
    (ranged-iterator this 0 cnt))

  IPrintWithWriter
  (-pr-writer [coll writer opts]
    (pr-sequential-writer writer pr-writer "[" " " "]" opts coll)))

(defn- default-key->prop [x]
  (when (keyword? x)
    (.-fqn x)))

(defn bean
  "Takes a JavaScript object and returns a read-only implementation of the map
  abstraction backed by the object.

  By default, bean produces beans that keywordize the keys. Supply
  :keywordize-keys false to suppress this behavior. You can alternatively
  supply :prop->key and :key->prop with functions that control the mapping
  between properties and keys.

  Supply :recursive true to create a bean which recursively converts
  JavaScript object values to beans and JavaScript arrays into vectors.

  Calling (bean) produces an empty bean."
  ([]
   (Bean. nil #js {} keyword default-key->prop false #js [] 0 nil))
  ([x]
   (Bean. nil x keyword default-key->prop false nil nil nil))
  ([x & opts]
   (let [{:keys [keywordize-keys prop->key key->prop recursive]} opts]
     (cond
       (false? keywordize-keys)
       (Bean. nil x identity identity (boolean recursive) nil nil nil)

       (and (some? prop->key) (some? key->prop))
       (Bean. nil x prop->key key->prop (boolean recursive) nil nil nil)

       :else
       (Bean. nil x keyword default-key->prop (boolean recursive) nil nil nil)))))

(defn bean?
  "Returns true if x is a bean."
  [x]
  (instance? Bean x))

(defn ^js object
  "Takes a bean and returns a JavaScript object."
  [b]
  (.-obj b))

;; TODO decide if these stay, given them docstrings

(defn ->clj [x]
  (->val x keyword default-key->prop))

(defn ->js [x]
  (cond
    (bean? x) (object x)
    (instance? ArrayVector x) (.-arr x)))
