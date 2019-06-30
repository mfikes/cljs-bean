;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns cljs-bean.from.cljs.core-test)

;; Copied
(defn seq-iter-match
  [coll]
  (let [i (-iterator coll)]
    (loop [s (seq coll)
           n 0]
      (if (seq s)
        (do
          (when-not (.hasNext i)
            (throw
              (js/Error.
                (str  "Iterator exhausted before seq at(" n ")" ))))
          (let [iv (.next i)
                sv (first s)]
            (when-not (= iv sv)
              (throw
                (js/Error.
                  (str "Iterator value " iv " and seq value " sv " did not match at ( "  n ")")))))
          (recur (rest s) (inc n)))
        (if (.hasNext i)
          (throw
            (js/Error.
              (str  "Seq exhausted before iterator at (" n ")")))
          true)))))
