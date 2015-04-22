(ns maze
  (:require [grid]
            [coordinate]))

(defn alloc []
  "Returns a 'default' maze.
  In the C++ version, this does allocations; in Clojure we don't actually really
  need this."
  {:grid        (grid/alloc)
   :work-queue  (list)
   :wall-vector []
   :src-vector  []
   :dst-vector  []})

(defn- string->int [s]
  "Converts s to integer, returns nil in case of error"
  (try
    (Integer/parseInt s)
    (catch NumberFormatException e
      nil)))

(defn- read-input-file [input-file-name]
  "Reads the input file, returns map with keys
  :width, :height, :depth, :work-list (unsorted), :srcs,
  :dsts, :walls."
  (reduce
    ; For each line l, updates the result map.
    (fn [res l]
      (println l)
      (let [[code x1_ y1_ z1_ x2_ y2_ z2_]
              (clojure.string/split l #" +")
            [x1 y1 z1 x2 y2 z2]
              (map string->int [x1_ y1_ z1_ x2_ y2_ z2_])]
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

(defn read [maze input-file-name]
  "Reads the given file and returns the maze it contains."
  (let [in   (read-input-file input-file-name)
        work (sort-by identity coordinate/compare-pairs
               (:work-list in))]
    (println "Maze dimensions =" (:width in) "x" (:height in) "x" (:depth in))
    (println "Paths to route  =" (count work))
    (-> maze
      (assoc-in [:grid :width]  (:width  in))
      (assoc-in [:grid :height] (:height in))
      (assoc-in [:grid :depth]  (:depth  in))
      (assoc :work-queue  work
             :wall-vector (:walls in)
             :src-vector  (:srcs in)
             :dst-vector  (:dsts in)))))
