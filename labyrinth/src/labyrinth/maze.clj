(ns labyrinth.maze
  (:refer-clojure :exclude [read])
  (:require [labyrinth.grid :as grid]
            [labyrinth.coordinate :as coordinate]
            [labyrinth.util :refer [str->int]]))

(defn alloc [grid work-queue walls srcs dsts]
  "Returns a maze based on the given parameters.

  In the C++ version, this does allocations; in Clojure we don't do this."
  {:grid        grid
   :work-queue  (ref work-queue) ; list, not vector!
   :wall-vector walls
   :src-vector  srcs
   :dst-vector  dsts})

; Note: C++ function addToGrid is embedded directly in read, where it is used.

(defn- read-input-file [input-file-name]
  "Reads the input file, returns map with keys
  :width, :height, :depth, :work-list (unsorted), :srcs,
  :dsts, :walls."
  (reduce
    ; For each line l, updates the result map.
    (fn [res l]
      (let [[code x1_ y1_ z1_ x2_ y2_ z2_]
              (clojure.string/split l #" +")
            [x1 y1 z1 x2 y2 z2]
              (map str->int [x1_ y1_ z1_ x2_ y2_ z2_])]
        (case code
          "" ; empty line: ignore
            res
          "#" ; comment: ignore
            res
          "d" ; dimensions: d x y z
            (-> res
              (assoc :width  x1)
              (assoc :height y1)
              (assoc :depth  z1))
          "p" ; paths: p x1 y1 z1 x2 y2 z2
            (let [src (coordinate/alloc x1 y1 z1)
                  dst (coordinate/alloc x2 y2 z2)]
              (-> res
                (update-in [:work-list] conj [src dst])
                (update-in [:srcs] conj src)
                (update-in [:dsts] conj dst)))
          "w" ; walls; w x y z
            (update-in res [:walls] conj
              (coordinate/alloc x1 y1 z1))
          ; default: error
            (do
              (println "Error reading line " l)
              res))))
    {:work-list (list)
     :srcs []
     :dsts []
     :walls []}
    (clojure.string/split-lines
      (slurp input-file-name))))

(defn- to-list [seq]
  (into (list) seq))

(defn read [input-file-name]
  "Reads the given file and returns the maze it contains."
  (let [in   (read-input-file input-file-name)
        work (to-list (sort-by identity coordinate/compare-pairs
               (:work-list in)))
        grid (grid/alloc-shared (:width in) (:height in) (:depth in))]
    (dosync ; Indicate walls, srcs, and dsts as full.
      (doseq [pt (concat (:walls in) (:srcs in) (:dsts in))]
        (ref-set (grid/get-point grid pt) :full)))
    (println "Maze dimensions =" (:width in) "x" (:height in) "x" (:depth in))
    (println "Paths to route  =" (count work))
    (alloc
      grid
      work
      (:walls in)
      (:srcs in)
      (:dsts in))))

(defn- check-path [test-grid i path errors]
  "Checks whether the given path is correct, and marks it with `i`.
  Updates errors if it isn't."
  (let [src-or-dst?  ; is p src or dst?
          (fn [p] (or (= p :src) (= p :dst)))
        ; check whether start = src or dst (a point can be both a src of one
        ; path and dst of other)
        errors1
          (if (not (src-or-dst? (grid/get-point test-grid (first path))))
            (conj errors (str "start of path " i " is not a source (but "
              (grid/get-point test-grid (first path)) ")"))
            errors)
        ; check whether end = src or dst (a point can be both a src of one
              ; path and dst of other)
        errors2
          (if (not (src-or-dst? (grid/get-point test-grid (last path))))
            (conj errors1 (str "end of path " i " is not a destination (but "
              (grid/get-point test-grid (last path)) ")"))
            errors1)
        ; check if points along path are not empty, if not, fill with "i"
        {test-grid2 :grid errors3 :errors}
          (reduce
            (fn [{test-grid :grid errors :errors} point]
              (if (not= (grid/get-point test-grid point) :empty)
                {:grid test-grid
                 :errors (conj errors (str "point " point
                   " is used by two paths: " (grid/get-point test-grid point)
                   " and " i))}
                {:grid (grid/set-point test-grid point i)
                 :errors errors}))
            {:grid test-grid :errors errors2}
            (rest (butlast path)))
        ; check whether all two subsequent points in the path are adjacent
        errors4
          (reduce
            (fn [errors j]
              (if-not (coordinate/adjacent? (nth path j) (nth path (inc j)))
                (conj errors (str "Points " j " (" (nth path j) ") and "
                  (inc j) " (" (nth path (inc j)) ") of path " i
                  " are not adjacent"))
                errors))
            errors3
            (range (dec (count path))))]
    {:grid test-grid2 :errors errors4}))

(defn check-paths [maze paths print?]
  "Check whether paths (single list of paths, each path is a list of points) are
  valid for maze. Prints maze with paths if `print?` is true."
  (let [shared-grid
          (:grid maze)
        {w :width h :height d :depth}
          shared-grid
        test-grid
          (grid/alloc-local w h d) ; starts with :empty, fills up with path ids
        {grid-with-paths :grid errors :errors}
          (as->
            {:grid test-grid :errors []}
            $
            ; mark walls as :full
            (reduce
              (fn [{test-grid :grid errors :errors} wall-pt]
                {:grid (grid/set-point test-grid wall-pt :wall) :errors errors})
              $
              (:walls maze))
            ; mark sources as :full
            (reduce
              (fn [{test-grid :grid errors :errors} src]
                {:grid (grid/set-point test-grid src :src) :errors errors})
              $
              (:src-vector maze))
            ; mark destinations as :full
            (reduce
              (fn [{test-grid :grid errors :errors} dst]
                {:grid (grid/set-point test-grid dst :dst) :errors errors})
              $
              (:dst-vector maze))
            ; make sure path is contiguous and does not overlap, mark it with
            ; its index
            (reduce
              (fn [{test-grid :grid errors :errors} [i path]]
                (check-path test-grid i path errors))
              $
              (map-indexed (fn [i p] [(inc i) p]) paths)))]
    (when-not (empty? errors)
      (println "Some errors occured:")
      (doseq [e errors] (println "* " e)))
    (if print?
      (grid/print grid-with-paths))
    (empty? errors)))
