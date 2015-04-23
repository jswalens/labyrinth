(ns grid)

(defn alloc [width height depth]
  "Returns an empty grid of the requested size.

  The C++ version ensures the points are aligned in the cache, we don't do
  this."
  {:width  width
   :height height
   :depth  depth
   :points (vec (repeatedly (* width height depth) #(ref :empty)))})

(defn copy [grid]
  "Make a local grid, copying the given grid.
  It has the same structure as a normal grid, except that its points are NOT
  put in refs."
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
  "Get the index of a 3D point in a 1D grid vector."
  (+ x (* (+ y (* z (:height grid))) (:width grid))))

(defn get-point [grid point]
  "Get a point in the grid, or throws an exception if not found."
  (nth (:points grid) (get-point-index grid point)))

(defn set-point [grid point v]
  "Set a point in the grid to `v`, returns updated grid.
  Works on local grid, not on global one (there, the point is a ref and should
  be updated directly)."
  (assoc-in grid [:points (get-point-index grid point)] v))

(defn add-path [grid points]
  "For each point in `points`, mark location in `grid` as full."
  (dosync
    (doseq [p points]
      (ref-set (get-point grid p) :full))))
