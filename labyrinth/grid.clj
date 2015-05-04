(ns grid
  (:require [coordinate]))

(defn alloc-shared [width height depth]
  "Returns an empty shared grid of the requested size.
  Points are refs containing either :empty or :full.

  The C++ version ensures the points are aligned in the cache, we don't do
  this in Clojure."
  {:width  width
   :height height
   :depth  depth
   :points (vec (repeatedly (* width height depth) #(ref :empty)))})

(defn alloc-local [width height depth]
  "Returns an empty local grid of the requested size.
  Points are either :empty or :full, not encapsulated in a ref.

  The C++ version ensures the points are aligned in the cache, we don't do
  this in Clojure."
  {:width  width
   :height height
   :depth  depth
   :points (vec (repeat (* width height depth) :empty))})

(defn copy [grid]
  "Make a local grid, copying the given shared grid.
  Points will be :empty, :full, or filled with a number; not encapsulated in a
  ref.

  Again, unlike the C++ version we don't care about cache alignment.
  Also, this is like the C++ version with USE_EARLY_RELEASE false."
  (dosync
    {:width  (:width grid)
     :height (:height grid)
     :depth  (:depth grid)
     :points (vec (map deref (:points grid)))}))

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
  "Get the index of a 3D point in a 1D grid vector.
  Works on local and shared grids."
  (+ x (* (+ y (* z (:height grid))) (:width grid))))

; C++ function grid_getPointIndices does the reverse of get-point-index:
; it converts an index in the 1D array to the x,y,z coordinates. We don't need
; that as we always pass around the x,y,z coordinates.

(defn get-point [grid point]
  "Get a point in the grid, or throws an exception if not found.
  Works on local and shared grids (for shared, it will return a ref)."
  (nth (:points grid) (get-point-index grid point)))

; C++ functions grid_isPointEmpty and grid_isPointFull are embedded directly
; where they are used.

(defn set-point [local-grid point v]
  "Set a point in the grid to `v`, returns updated grid.
  Works on local grid, not on shared one (there, the point is a ref and should
  be updated directly)."
  (assoc-in local-grid [:points (get-point-index local-grid point)] v))

(defn add-path [grid path]
  "Set all points in `path` as full. Only works on shared grid."
  (dosync
    (doseq [point path]
      (ref-set (get-point grid point) :full))))

(defn print [grid]
  "Print grid, used by maze/check-paths."
  (doseq [z (range (:depth grid))]
    (printf "[z = %d]\n" z)
    (doseq [x (range (:width grid))]
      (doseq [y (range (:height grid))]
        (printf "%4s" (grid/get-point grid (coordinate/alloc x y z))))
      (println))
    (println)))
