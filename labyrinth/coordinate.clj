(ns coordinate)

(defn alloc [x y z]
  "Returns a new coordinate.
  In the C++ version, this does allocations; in Clojure we don't actually really
  need this."
  {:x x
   :y y
   :z z})

(defn distance [a b]
  "Euclidean distance between two coordinates."
  (let [dx (- (:x a) (:x b))
        dy (- (:y a) (:y b))
        dz (- (:z a) (:z b))]
    (Math/sqrt (+ (* dx dx) (* dy dy) (* dz dz)))))

(defn compare-pairs [[a1 b1] [a2 b2]]
  "Compare two coordinate pairs, by their distance.
  Longer paths first so they are more likely to succeed."
  (- (compare (distance a1 b1) (distance a2 b2))))
