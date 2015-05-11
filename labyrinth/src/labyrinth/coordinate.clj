(ns labyrinth.coordinate)

(defn alloc [x y z]
  "Returns a new coordinate.

  In the C++ version, this does an allocation; in Clojure we don't actually
  really need this function."
  {:x x
   :y y
   :z z})

(defn equal? [a b]
  "Are the two points equal?"
  (and
    (= (:x a) (:x b))
    (= (:y a) (:y b))
    (= (:z a) (:z b))))

(defn distance [a b]
  "Euclidean distance between two coordinates."
  (let [dx (- (:x a) (:x b))
        dy (- (:y a) (:y b))
        dz (- (:z a) (:z b))]
    (Math/sqrt (+ (* dx dx) (* dy dy) (* dz dz)))))

(defn compare-pairs [[a1 b1] [a2 b2]]
  "Compare two coordinate pairs, by their distance."
  (- (compare (distance a1 b1) (distance a2 b2))))

(defn adjacent? [a b]
  "Returns true if the two points are adjacent."
  (= (distance a b) 1.0))

(defn step-to [dir point]
  "Calculate point with step taken in given direction."
  (case dir
    :x-pos (update-in point [:x] inc)
    :x-neg (update-in point [:x] dec)
    :y-pos (update-in point [:y] inc)
    :y-neg (update-in point [:y] dec)
    :z-pos (update-in point [:z] inc)
    :z-neg (update-in point [:z] dec)))
