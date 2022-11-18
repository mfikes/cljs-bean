(ns cljs-bean.core
  (:require
   [cljs-bean.from.cljs.core :as core]
   [goog.object :as gobj]))

(declare Bean)
(declare ArrayVector)
(declare ->clj)

(defprotocol BeanContext
  (keywords? [_])
  (key->prop [_ key'])
  (prop->key [_ prop])
  (transform [_ v p k n]))

(def ^:private lookup-sentinel #js {})

(defn primitive? [x]
  (or (number? x)
      (string? x)
      (boolean? x)
      (nil? x)))

(defn- unwrap [x]
  (cond
    (primitive? x) x
    (instance? Bean x) (.-obj x)
    (instance? ArrayVector x) (.-arr x)
    :else x))

(def ^:private empty-map (.. js/cljs -core -PersistentArrayMap -EMPTY))

(defn- snapshot [x ctx recursive?]
  (let [result (volatile! (transient empty-map))]
    (gobj/forEach x (fn [v p _]
                      (let [k (prop->key ctx p)]
                        (vswap! result assoc! k
                                (if recursive? (transform ctx v p k nil) #_else v)))))
    (persistent! @result)))

(defn- snapshot-arr [arr]
  (vec (amap arr idx ret (->clj (aget arr idx)))))

(defn- indexed-entry [obj ctx ^boolean recursive? arr i]
  (let [prop (aget arr i)
        k    (prop->key ctx prop)]
    (MapEntry. k
      (let [v (unchecked-get obj prop)]
        (if recursive? (transform ctx v prop k nil) 
         #_else v))
      nil)))

(defn- compatible-key? [k ctx]
  (if (keywords? ctx)
    (keyword? k)
    (string? k)))

(defn- compatible-value? [v recursive?]
  (or (primitive? v)
      (and (not (or (and (map? v) (not (instance? Bean v)))
                    (and (vector? v) (not (instance? ArrayVector v)))))
           (not (and recursive?
                     (or (object? v)
                         (array? v)))))))

(defn- snapshot? [k v ctx recursive?]
  (not (and (compatible-key? k ctx)
            (compatible-value? v recursive?))))

(deftype ^:private TransientBean [^:mutable ^boolean editable?
                                  obj ^BeanContext ctx ^boolean recursive?
                                  ^:mutable __cnt]
  ILookup
  (-lookup [_ k]
    (if editable?
      (let [p (key->prop ctx k)
            v (unchecked-get obj p)]
        (if recursive? (transform ctx v p k nil) #_else v))
      (throw (js/Error. "lookup after persistent!"))))
  (-lookup [_ k not-found]
    (if editable?
      (let [p   (key->prop ctx k)
            ret (gobj/get obj p not-found)]
        (if (and recursive? (not (identical? ret not-found)))
          (transform ctx ret p k nil)
          ret))
      (throw (js/Error. "lookup after persistent!"))))

  ICounted
  (-count [_]
    (if (nil? __cnt)
      (set! __cnt (count (js-keys obj)))
      __cnt))

  ITransientCollection
  (-conj! [tcoll o]
    (core/TransientArrayMap-conj! tcoll o editable?))

  (-persistent! [tcoll]
    (if editable?
      (do
        (set! editable? false)
        (Bean. nil obj ctx recursive? nil __cnt nil))
      (throw (js/Error. "persistent! called twice"))))

  ITransientAssociative
  (-assoc! [tcoll k v]
    (if editable?
      (if (snapshot? k v ctx recursive?)
        (-assoc! (transient (snapshot obj ctx recursive?)) k v)
        (do
          (unchecked-set obj (key->prop ctx k) (cond-> v recursive? unwrap))
          (set! __cnt nil)
          tcoll))
      (throw (js/Error. "assoc! after persistent!"))))

  ITransientMap
  (-dissoc! [tcoll k]
    (if editable?
      (do
        (js-delete obj (key->prop ctx k))
        (set! __cnt nil)
        tcoll)
      (throw (js/Error. "dissoc! after persistent!"))))

  IFn
  (-invoke [_ k]
    (if editable?
      (let [p (key->prop ctx k)
            v (unchecked-get obj p)]
        (if recursive? (transform ctx v p k nil)
            #_else v))
      (throw (js/Error. "lookup after persistent!"))))
  (-invoke [_ k not-found]
    (if editable?
      (let [p   (key->prop ctx k)
            ret (gobj/get obj p not-found)]
        (if (and recursive? (not (identical? ret not-found))) (transform ctx ret p k nil)
            #_else ret))
      (throw (js/Error. "lookup after persistent!")))))

(deftype ^:private BeanIterator [obj ctx ^boolean recursive? arr ^:mutable i cnt]
  Object
  (hasNext [_]
    (< i cnt))
  (next [_]
    (let [ret (indexed-entry obj ctx recursive? arr i)]
      (set! i (inc i))
      ret)))

(deftype ^:private BeanSeq [obj ctx ^boolean recursive? arr i meta]
  Object
  (toString [coll]
    (pr-str* coll))
  (equiv [this other]
    (-equiv this other))
  (indexOf [coll x]
    (core/-indexOf coll x 0))
  (indexOf [coll x start]
    (core/-indexOf coll x start))
  (lastIndexOf [coll x]
    (core/-lastIndexOf coll x (count coll)))
  (lastIndexOf [coll x start]
    (core/-lastIndexOf coll x start))

  ICloneable
  (-clone [_] (BeanSeq. obj ctx recursive? arr i meta))

  ISeqable
  (-seq [this] this)

  IMeta
  (-meta [_] meta)
  IWithMeta
  (-with-meta [coll new-meta]
    (if (identical? new-meta meta)
      coll
      (BeanSeq. obj ctx recursive? arr i new-meta)))

  ASeq
  ISeq
  (-first [_] (indexed-entry obj ctx recursive? arr i))
  (-rest [_] (if (< (inc i) (alength arr))
               (BeanSeq. obj ctx recursive? arr (inc i) nil)
               ()))

  INext
  (-next [_] (if (< (inc i) (alength arr))
               (BeanSeq. obj ctx recursive? arr (inc i) nil)
               nil))

  ICounted
  (-count [_]
    (max 0 (- (alength arr) i)))

  IIndexed
  (-nth [_ n]
    (let [i (+ n i)]
      (if (and (<= 0 i) (< i (alength arr)))
        (indexed-entry obj ctx recursive? arr i)
        (throw (js/Error. "Index out of bounds")))))
  (-nth [_ n not-found]
    (let [i (+ n i)]
      (if (and (<= 0 i) (< i (alength arr)))
        (indexed-entry obj ctx recursive? arr i)
        not-found)))

  ISequential
  IEquiv
  (-equiv [coll other]
    (core/equiv-sequential coll other))

  ICollection
  (-conj [coll o] (cons o coll))

  IEmptyableCollection
  (-empty [_] ())

  IReduce
  (-reduce [coll f]
    (core/ci-reduce coll f))
  (-reduce [coll f start]
    (core/ci-reduce coll f start))

  IHash
  (-hash [coll] (hash-ordered-coll coll))

  IPrintWithWriter
  (-pr-writer [coll writer opts]
    (pr-sequential-writer writer pr-writer "(" " " ")" opts coll)))

(deftype Bean [meta obj ^BeanContext ctx ^boolean recursive?
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
  (-clone [_] (Bean. meta obj ctx recursive? __arr __cnt __hash))

  IWithMeta
  (-with-meta [coll new-meta]
    (if (identical? new-meta meta)
      coll
      (Bean. new-meta obj ctx recursive? __arr __cnt __hash)))

  IMeta
  (-meta [_] meta)

  ICollection
  (-conj [coll entry]
    (core/PersistentArrayMap-conj coll entry))

  IEmptyableCollection
  (-empty [_] (Bean. meta #js {} ctx recursive? #js []  0 nil))

  IEquiv
  (-equiv [coll other]
    (equiv-map coll other))

  IHash
  (-hash [coll] (caching-hash coll hash-unordered-coll __hash))

  IIterable
  (-iterator [coll]
    (when (nil? __arr)
      (set! __arr (js-keys obj)))
    (BeanIterator. obj ctx recursive? __arr 0 (-count coll)))

  ISeqable
  (-seq [_]
    (when (nil? __arr)
      (set! __arr (js-keys obj)))
    (when (pos? (alength __arr))
      (BeanSeq. obj ctx recursive? __arr 0 nil)))

  IAssociative
  (-assoc [_ k v]
    (if (snapshot? k v ctx recursive?)
      (-assoc (with-meta (snapshot obj ctx recursive?) meta) k v)
      (Bean. meta
        (doto (gobj/clone obj) (unchecked-set (key->prop ctx k) (cond-> v recursive? unwrap)))
        ctx recursive? nil nil nil)))

  (-contains-key? [coll k]
    (not (identical? (-lookup coll k lookup-sentinel) lookup-sentinel)))

  IFind
  (-find [_ k]
    (let [p (key->prop ctx k)
          v (gobj/get obj p lookup-sentinel)]
      (when-not (identical? v lookup-sentinel)
        (MapEntry. k (if recursive? (transform ctx v p k nil) #_else v) nil))))

  IMap
  (-dissoc [_ k]
    (Bean. meta (doto (gobj/clone obj) (js-delete (key->prop ctx k)))
      ctx recursive? nil nil nil))

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
    (let [p (key->prop ctx k)
          v (unchecked-get obj p)]
      (if recursive? (transform ctx v p k nil) #_else v)))
  (-lookup [_ k not-found]
    (let [p   (key->prop ctx k)
          ret (gobj/get obj p not-found)]
      (if (and recursive? (not (identical? ret not-found))) (transform ctx ret p k nil)
          #_else ret)))

  IKVReduce
  (-kv-reduce [_ f init]
    (try
      (let [result (volatile! init)]
        (gobj/forEach obj
          (fn [v p _]
            (let [k (prop->key ctx p)
                  r (vswap! result f k
                            (if recursive? (transform ctx v p k nil) #_else v))]
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
    (let [p (key->prop ctx k)
          v (unchecked-get obj p)]
      (if recursive? (transform ctx v p k nil) #_else v)))

  (-invoke [_ k not-found]
    (let [p   (key->prop ctx k)
          ret (gobj/get obj p not-found)]
      (if (and recursive? (not (identical? ret not-found))) (transform ctx ret p k nil)
          #_else ret)))

  IEditableCollection
  (-as-transient [_]
    (TransientBean. true (gobj/clone obj) ctx recursive? __cnt))

  IPrintWithWriter
  (-pr-writer [coll writer opts]
    (print-map coll pr-writer writer opts)))

(deftype ^:private TransientArrayVector [^:mutable ^boolean editable?
                                         ^:mutable arr ctx]
  ITransientCollection
  (-conj! [tcoll o]
    (if editable?
      (if (not (compatible-value? o true))
        (-conj! (transient (snapshot-arr arr)) o)
        (do
          (.push arr (unwrap o))
          tcoll))
      (throw (js/Error. "conj! after persistent!"))))

  (-persistent! [_]
    (if editable?
      (do
        (set! editable? false)
        (ArrayVector. nil ctx arr nil))
      (throw (js/Error. "persistent! called twice"))))

  ITransientAssociative
  (-assoc! [tcoll key val]
    (core/TransientVector-assoc! tcoll key val "TransientArrayVector"))

  ITransientVector
  (-assoc-n! [tcoll n val]
    (if editable?
      (if (not (compatible-value? val true))
        (-assoc-n! (transient (snapshot-arr arr)) n val)
        (cond
          (and (<= 0 n) (< n (alength arr)))
          (do (aset arr n (unwrap val))
              tcoll)
          (== n (alength arr)) (-conj! tcoll val)
          :else
          (throw
            (js/Error.
              (str "Index " n " out of bounds for TransientArrayVector of length" (alength arr))))))
      (throw (js/Error. "assoc! after persistent!"))))

  (-pop! [tcoll]
    (if editable?
      (if (zero? (alength arr))
        (throw (js/Error. "Can't pop empty vector"))
        (do
          (set! arr (.slice arr 0 (dec (alength arr))))
          tcoll))
      (throw (js/Error. "pop! after persistent!"))))

  ICounted
  (-count [_]
    (if editable?
      (alength arr)
      (throw (js/Error. "count after persistent!"))))

  IIndexed
  (-nth [_ n]
    (if editable?
      (transform ctx (aget arr n) nil nil nth)
      (throw (js/Error. "nth after persistent!"))))

  (-nth [coll n not-found]
    (if (and (<= 0 n) (< n (alength arr)))
      (-nth coll n)
      not-found))

  ILookup
  (-lookup [coll k] (-lookup coll k nil))

  (-lookup [coll k not-found] (if (number? k)
                                (-nth coll k not-found)
                                not-found))

  IFn
  (-invoke [coll k]
    (-lookup coll k))

  (-invoke [coll k not-found]
    (-lookup coll k not-found)))

(deftype ^:private ArrayVectorIterator [ctx arr ^:mutable i cnt]
  Object
  (hasNext [_]
    (< i cnt))
  (next [_]
    (let [ret (transform ctx (aget arr i) nil nil i)]
      (set! i (inc i))
      ret)))

(deftype ^:private ArrayVectorSeq [ctx arr i meta]
  Object
  (toString [coll]
    (pr-str* coll))
  (equiv [this other]
    (-equiv this other))
  (indexOf [coll x]
    (core/-indexOf coll x 0))
  (indexOf [coll x start]
    (core/-indexOf coll x start))
  (lastIndexOf [coll x]
    (core/-lastIndexOf coll x (count coll)))
  (lastIndexOf [coll x start]
    (core/-lastIndexOf coll x start))

  ICloneable
  (-clone [_] (ArrayVectorSeq. ctx arr i meta))

  ISeqable
  (-seq [this] this)

  IMeta
  (-meta [_] meta)
  IWithMeta
  (-with-meta [coll new-meta]
    (if (identical? new-meta meta)
      coll
      (ArrayVectorSeq. ctx arr i new-meta)))

  ASeq
  ISeq
  (-first [_] (transform ctx (aget arr i) nil nil i))
  (-rest [_] (if (< (inc i) (alength arr))
               (ArrayVectorSeq. ctx arr (inc i) nil)
               ()))

  INext
  (-next [_] (if (< (inc i) (alength arr))
               (ArrayVectorSeq. ctx arr (inc i) nil)
               nil))

  ICounted
  (-count [_]
    (max 0 (- (alength arr) i)))

  IIndexed
  (-nth [_ n]
    (let [i (+ n i)]
      (if (and (<= 0 i) (< i (alength arr)))
        (transform ctx (aget arr i) nil nil i)
        (throw (js/Error. "Index out of bounds")))))
  (-nth [_ n not-found]
    (let [i (+ n i)]
      (if (and (<= 0 i) (< i (alength arr)))
        (transform ctx (aget arr i) nil nil i)
        not-found)))

  ISequential
  IEquiv
  (-equiv [coll other]
    (core/equiv-sequential coll other))

  ICollection
  (-conj [coll o] (cons o coll))

  IEmptyableCollection
  (-empty [_] ())

  IReduce
  (-reduce [coll f]
    (core/ci-reduce coll f))
  (-reduce [coll f start]
    (core/ci-reduce coll f start))

  IHash
  (-hash [coll] (hash-ordered-coll coll))

  IPrintWithWriter
  (-pr-writer [coll writer opts]
    (pr-sequential-writer writer pr-writer "(" " " ")" opts coll)))

(deftype ArrayVector [meta ctx arr ^:mutable __hash]
  Object
  (toString [coll]
    (pr-str* coll))
  (equiv [this other]
    (-equiv this other))
  (indexOf [coll x]
    (core/-indexOf coll x 0))
  (indexOf [coll x start]
    (core/-indexOf coll x start))
  (lastIndexOf [coll x]
    (core/-lastIndexOf coll x))
  (lastIndexOf [coll x start]
    (core/-lastIndexOf coll x start))

  ICloneable
  (-clone [_] (ArrayVector. meta ctx arr __hash))

  IWithMeta
  (-with-meta [coll new-meta]
    (if (identical? new-meta meta)
      coll
      (ArrayVector. new-meta ctx arr __hash)))

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
          (ArrayVector. meta ctx
            (.slice new-arr 0 (dec (alength new-arr))) nil))))

  ICollection
  (-conj [_ o]
    (if (not (compatible-value? o true))
      (-conj (snapshot-arr arr) o)
      (let [new-arr (aclone arr)]
        (unchecked-set new-arr (alength new-arr) (unwrap o))
        (ArrayVector. meta ctx new-arr nil))))

  IEmptyableCollection
  (-empty [coll]
    (ArrayVector. meta ctx #js [] nil))

  ISequential
  IEquiv
  (-equiv [coll other]
    (core/PersistentVector-equiv coll other ArrayVector (alength arr)))

  IHash
  (-hash [coll] (caching-hash coll hash-ordered-coll __hash))

  ISeqable
  (-seq [coll]
    (when (pos? (alength arr))
      (ArrayVectorSeq. ctx arr 0 nil)))

  ICounted
  (-count [coll] (alength arr))

  IIndexed
  (-nth [coll n]
    (if (and (<= 0 n) (< n (alength arr)))
      (transform ctx (aget arr n) nil nil n)
      (throw (js/Error. (str "No item " n " in vector of length " (alength arr))))))
  (-nth [coll n not-found]
    (if (and (<= 0 n) (< n (alength arr)))
      (transform ctx (aget arr n) nil nil n)
      not-found))

  ILookup
  (-lookup [coll k] (-lookup coll k nil))
  (-lookup [coll k not-found] (core/PersistentVector-lookup coll k not-found))

  IAssociative
  (-assoc [coll k v]
    (core/PersistentVector-assoc coll k v))
  (-contains-key? [coll k]
    (core/PersistentVector-contains-key? coll k (alength arr)))

  IFind
  (-find [coll n]
    (when (and (<= 0 n) (< n (alength arr)))
      (MapEntry. n (transform ctx (aget arr n) nil nil n) nil)))

  IVector
  (-assoc-n [coll n val]
    (cond
      (and (<= 0 n) (< n (alength arr)))
      (if (not (compatible-value? val true))
        (-assoc-n (snapshot-arr arr) n val)
        (let [new-arr (aclone arr)]
          (aset new-arr n (unwrap val))
          (ArrayVector. meta ctx new-arr nil)))
      (== n (alength arr)) (-conj coll val)
      :else (throw (js/Error. (str "Index " n " out of bounds  [0," (alength arr) "]")))))


  IReduce
  (-reduce [v f]
    (core/ci-reduce v f))
  (-reduce [v f init]
    (core/ci-reduce v f init))


  IKVReduce
  (-kv-reduce [v f init]
    ;; Derived from PersistentVector -kv-reduce
    (loop [i 0 init init]
      (if (< i (alength arr))
        (let [len  (alength arr)
              init (loop [j 0 init init]
                     (if (< j len)
                       (let [init (f init (+ j i) (transform ctx (aget arr j) nil nil j))]
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

  IEditableCollection
  (-as-transient [coll]
    (TransientArrayVector. true (aclone arr) ctx))

  IReversible
  (-rseq [coll]
    (when (pos? (alength arr))
      (RSeq. coll (dec (alength arr)) nil)))

  IIterable
  (-iterator [_]
    (ArrayVectorIterator. ctx arr 0 (alength arr)))

  IComparable
  (-compare [x y]
    (if (vector? y)
      (core/compare-indexed x y)
      (throw (js/Error. (str "Cannot compare " x " to " y)))))

  IPrintWithWriter
  (-pr-writer [coll writer opts]
    (pr-sequential-writer writer pr-writer "[" " " "]" opts coll)))

(defn- default-key->prop [x]
  (when (keyword? x)
    (.-fqn x)))

(defn- ->val [ctx x]
  (cond
    (primitive? x) x
    (object? x) (Bean. nil x ctx true nil nil nil)
    (array? x) (ArrayVector. nil ctx x nil)
    :else x))

(def ^:private keywordize-ctx
  (reify BeanContext
    (keywords? [_] true)
    (key->prop [_ x] (default-key->prop x))
    (prop->key [_ prop] (keyword prop))
    (transform [ctx v _ _ _] (->val ctx v))))

(def ^:private identity-ctx
  (reify BeanContext
    (keywords? [_] false)
    (key->prop [_ x] x)
    (prop->key [_ prop] prop)
    (transform [ctx v _ _ _] (->val ctx v))))

(deftype K-Transform [key->prop' prop->key']
  BeanContext
  (keywords? [_] (identical? key->prop' keyword))
  (key->prop [_ x] (key->prop' x))
  (prop->key [_ prop] (prop->key' prop))
  (transform [ctx v _ _ _] (->val ctx v)))

(deftype V-Transform [transform']
  BeanContext
  (keywords? [_] false)
  (key->prop [_ x] x)
  (prop->key [_ prop] prop)
  (transform [ctx v _ _ _]
    (if-some [transformed (transform' v)] transformed
      #_else (->val ctx v))))

(deftype KV-Transform [key->prop' prop->key' transform']
  BeanContext
  (keywords? [_] (identical? prop->key' keyword))
  (key->prop [_ x] (key->prop' x))
  (prop->key [_ prop] (prop->key' prop))
  (transform [ctx v _ _ _]
    (if-some [transformed (transform' v)]
      transformed #_else (->val ctx v))))

(defn bean-context [{:keys [prop->key key->prop transform keywordize-keys]}]
  (if (nil? transform)
    (cond
      (false? keywordize-keys)
      , identity-ctx

      (and (some? prop->key) (some? key->prop))
      , (->K-Transform key->prop prop->key)

      :else
      , keywordize-ctx)
   ;else transform
    (if (false? keywordize-keys)
      (->V-Transform transform)
      ;else
      (->KV-Transform (or key->prop default-key->prop) (or prop->key keyword) transform))))

(defn bean
  "Takes a JavaScript object and returns a read-only implementation of the map
  abstraction backed by the object.

  By default, bean produces beans that keywordize the keys. Supply
  :keywordize-keys false to suppress this behavior. You can alternatively
  supply :prop->key and :key->prop with functions that control the mapping
  between properties and keys.

  Supply :recursive true to create a bean which recursively converts
  JavaScript object values to beans and JavaScript arrays into vectors.

  Supply :transform and a function of one argument to transform values being
  converted from JavaScript to ClojureScript. This function should return nil
  if no conversion is to be performed, thus allowing default logic to be applied.

  Calling (bean) produces an empty bean."
  ([]
   (Bean. nil #js {} keywordize-ctx false #js [] 0 nil))
  ([x]
   (Bean. nil x keywordize-ctx false nil nil nil))
  ([x & {:keys [context recursive] :as opts}]
   (Bean. nil x (or context (bean-context opts)) (boolean recursive) nil nil nil)))

(defn bean?
  "Returns true if x is a bean."
  [x]
  (instance? Bean x))

(defn ^js object
  "Takes a bean and returns a JavaScript object."
  [b]
  (.-obj b))

(defn ->clj
  "Recursively converts JavaScript values to ClojureScript.

  JavaScript objects are converted to beans with keywords for keys.

  JavaScript arrays are converted to read-only implementations of the vector
  abstraction, backed by the supplied array.

  By default, ->clj produces values that keywordize the keys. Supply
  :keywordize-keys false to suppress this behavior. You can alternatively
  supply :prop->key and :key->prop with functions that control the mapping
  between properties and keys.

  Supply :transform and a function of one argument to transform values being
  converted from JavaScript to ClojureScript. This function should return nil
  if no conversion is to be performed, thus allowing default logic to be applied."
  ([x]
   (->val keywordize-ctx x))
  ([x & {:keys [context] :as opts}]
   (->val (or context (bean-context opts)) x)))

(defn ->js
  "Recursively converts ClojureScript values to JavaScript.

  Where possible, directly returns the backing objects and arrays for values
  produced using ->clj and bean.

  Otherwise delegates to clj->js to perform the conversion, converting keyword
  keys using their qualified names. You can alternatively supply :key->prop with
  a function that controls the mapping from keys to properties."
  ([x]
   (cond
     (instance? Bean x) (.-obj x)
     (instance? ArrayVector x) (.-arr x)
     :else (clj->js x :keyword-fn default-key->prop)))
  ([x & opts]
   (cond
     (instance? Bean x) (.-obj x)
     (instance? ArrayVector x) (.-arr x)
     :else (let [{:keys [key->prop]
                  :or   {key->prop default-key->prop}} opts]
             (clj->js x :keyword-fn key->prop)))))

(defn- set-empty-colls!
  "Set empty map and array to Bean and ArrayVector. Useful for testing."
  []
  (set! (.. js/cljs -core -PersistentArrayMap -EMPTY) (->clj #js {}))
  (set! (.. js/cljs -core -PersistentVector -EMPTY) (->clj #js []))
  nil)
