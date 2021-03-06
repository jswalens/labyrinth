(ns labyrinth.grid
  (:refer-clojure :exclude [print])
  (:require [random]
            [labyrinth.coordinate :as coordinate]))

(defn alloc [width height depth]
  "Returns an empty shared grid of the requested size.
  Points are refs containing either :empty or :full.

  The C++ version ensures the points are aligned in the cache, we don't do
  this in Clojure."
  {:width  width
   :height height
   :depth  depth
   :costs  (vec (repeatedly (* width height depth) #(random/rand-int 5)))
   :points (vec (repeatedly (* width height depth) #(ref :empty)))})

(defn min-grid-point [a b]
  "Between two grid points `a` and `b`, return the minimum.
  Order: :empty < 0 < 1 < ... < inf < :full."
  (cond
    (= a :full)  :full
    (= b :full)  :full
    (= a :empty) b
    (= b :empty) a
    :else        (min a b)))

(defn copy-local [grid]
  "Copy a shared grid to a local grid.
  Points will be :empty, :full, or filled with a number.

  Again, unlike the C++ version we don't care about cache alignment.
  Also, this is like the C++ version with USE_EARLY_RELEASE false."
  (dosync
    {:width  (:width grid)
     :height (:height grid)
     :depth  (:depth grid)
     :costs  (:costs grid)
     :points (vec
               (map
                 #(ref (deref %) :resolve (fn [o p c] (min-grid-point p c)))
                 (:points grid)))}))

(defn is-point-valid? [grid {x :x y :y z :z}]
  "Is the point valid, i.e. within the boundaries of `grid`?"
  (and
    (>= x 0)
    (>= y 0)
    (>= z 0)
    (< x (:width grid))
    (< y (:height grid))
    (< z (:depth grid))))

(defn get-point-index [grid {x :x y :y z :z}]
  "Get the index of a 3D point in a 1D grid vector."
  (+ x (* (+ y (* z (:height grid))) (:width grid))))

; C++ function grid_getPointIndices does the reverse of get-point-index:
; it converts an index in the 1D array to the x,y,z coordinates. We don't need
; that as we always pass around the x,y,z coordinates.

(defn get-point [grid point]
  "Get a point in the grid, or throws an exception if not found."
  @(nth (:points grid) (get-point-index grid point)))

; C++ functions grid_isPointEmpty and grid_isPointFull are embedded directly
; where they are used.

(defn set-point [grid point v]
  "Set a point in the grid to `v`."
  (ref-set (nth (:points grid) (get-point-index grid point)) v))

(defn get-point-cost [grid point]
  "Get the cost associated to a point in the grid, or throws an exception if
  point not found."
  (nth (:costs grid) (get-point-index grid point)))

(defn add-path [grid path]
  "Set all points in `path` as full."
  (dosync
    (doseq [point path]
      (set-point grid point :full))))

(defn- print-point [val]
  (case val
    :empty "  . "
    :full  "  X "
    :src   "  S "
    :dst   "  D "
           (format "%3s " val)))

(defn print [grid]
  "Print grid, used by maze/check-paths."
  (doseq [z (range (:depth grid))]
    (printf "[z = %d]\n" z)
    (doseq [x (range (:width grid))]
      (doseq [y (range (:height grid))]
        (clojure.core/print (print-point
          (get-point grid (coordinate/alloc x y z)))))
      (println))
    (println)))
