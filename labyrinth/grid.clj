(ns grid
  (:require [coordinate]))

(defn alloc [width height depth]
  "Returns an empty shared grid of the requested size.
  Points are either :empty or :full.

  The C++ version ensures the points are aligned in the cache, we don't do
  this."
  {:width  width
   :height height
   :depth  depth
   :points (vec (repeatedly (* width height depth) #(ref :empty)))})

(defn copy [grid]
  "Make a local grid, copying the given grid.
  It has the same structure as a normal grid, except that its points are NOT
  put in refs.
  Points will be :empty, :full, or filled with a number."
  (dosync
    {:width  (:width grid)
     :height (:height grid)
     :depth  (:depth grid)
     :points (vec (map deref (:points grid)))}))

(defn is-point-valid? [grid {x :x y :y z :z}]
  "Is point within the boundaries of grid?"
  (and
    (>= x 0)
    (>= y 0)
    (>= z 0)
    (< x (:width grid))
    (< y (:height grid))
    (< z (:depth grid))))

(defn get-point-index [grid {x :x y :y z :z}]
  "Get the index of a 3D point in a 1D grid vector.
  Works on local and shared grids."
  (+ x (* (+ y (* z (:height grid))) (:width grid))))

(defn get-point [grid point]
  "Get a point in the grid, or throws an exception if not found.
  Works on local and shared grids (for shared, it will return a ref)."
  (nth (:points grid) (get-point-index grid point)))

(defn set-point [local-grid point v]
  "Set a point in the grid to `v`, returns updated grid.
  Works on local grid, not on shared one (there, the point is a ref and should
  be updated directly)."
  (assoc-in local-grid [:points (get-point-index local-grid point)] v))

(defn print [grid]
  "Print grid, used by maze/check-paths."
  (doseq [z (range (:depth grid))]
    (printf "[z = %d]\n" z)
    (doseq [x (range (:width grid))]
      (doseq [y (range (:height grid))]
        (printf "%4s" (grid/get-point grid (coordinate/alloc x y z))))
      (println))
    (println)))
