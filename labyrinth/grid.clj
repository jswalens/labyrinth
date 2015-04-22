(ns grid)

(defn alloc []
  "Returns a 'default' grid.
  In the C++ version, this does allocations; in Clojure we don't actually really
  need this. The C++ version also ensures the points are aligned in the cache,
  we don't do this."
  {:width  nil
   :height nil
   :depth  nil
   :points []})

(defn is-point-valid? [grid {x :x y :y z :z}]
  "Is point within the boundaries of grid?"
  (and
    (>= x 0)
    (>= y 0)
    (>= z 0)
    (< x (:width grid)
    (< y (:height grid))
    (< z (:depth grid)))))

(defn get-point-index [grid {x :x y :y z :z}]
  "Get the index of a 3D point in a 1D grid vector."
  (+ x (* (+ y (* z (:height grid))) (:width grid))))

(defn get-point [grid point]
  "Get a point in the grid, or :empty if not found."
  (nth (:points grid) (get-point-index grid point) :empty))

(defn set-point [grid point v]
  "Set a point in the grid to `v`, returns updated grid."
  (assoc-in grid [:points (get-point-index grid point)] v))

(defn add-path [grid points]
  "For each point in `points`, mark location in `grid` as full.
  Returns updated grid."
  (reduce
    (fn [grid point]
      (set-point grid point :full))
    grid
    points))
