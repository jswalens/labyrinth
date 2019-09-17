(ns labyrinth.router
  (:require [labyrinth.coordinate :as coordinate]
            [labyrinth.grid :as grid]
            [labyrinth.util :refer [dosync-tracked]]
            [taoensso.tufte :as tufte :refer [defnp p]])
  (:import [java.io StringWriter]
           [java.util LinkedList]
           [java.util.concurrent ConcurrentHashMap]))

; Note: C++ function router_alloc is not needed, we just pass the parameters
; directly.
;(defn alloc [x-cost y-cost z-cost bend-cost]
;  "Returns a list of router paramaters."
;  {:x-cost    x-cost
;   :y-cost    y-cost
;   :z-cost    z-cost
;   :bend-cost bend-cost})

;(def log println)
(defn log [& _] nil)

(defnp score [local-grid current next params]
  (let [current-value (grid/get-point local-grid current)
        directional-costs
          (for [[dir cost] [[:x :x-cost] [:y :y-cost] [:z :z-cost]]]
            (if (not= (dir current) (dir next)) ; changed in this direction
              (cost params)
              0))]
    (+ current-value
       (reduce + directional-costs)
       (grid/get-point-cost local-grid next))))

(defnp expand-point [local-grid {x :x y :y z :z :as point} params]
  "Expands one step past `point`, i.e. to the neighbors of `point`.
  A neighbor is still to be expanded if it not full (i.e. a wall), and either:
  1. has no path to it yet (it is empty), or
  2. has a longer path to it (its current value > value of `point` + cost to go
     to it).
  This function returns the neighbors to expand next."
  (dosync
    (let [all-neighbors
            [{:x (+ x 1) :y    y    :z    z   }
             {:x (- x 1) :y    y    :z    z   }
             {:x    x    :y (+ y 1) :z    z   }
             {:x    x    :y (- y 1) :z    z   }
             {:x    x    :y    y    :z (+ z 1)}
             {:x    x    :y    y    :z (- z 1)}]
          valid-neighbors
            (filter #(grid/is-point-valid? local-grid %) all-neighbors)
          neighbors-with-value
            (map #(assoc % :value (score local-grid point % params)) valid-neighbors)
          neighbors-to-expand
            (filter
              (fn [neighbor]
                (let [nb-current-value (grid/get-point local-grid neighbor)]
                  (and
                    (not= nb-current-value :full)
                    (or
                      (= nb-current-value :empty)
                      (< (:value neighbor) nb-current-value)))))
              neighbors-with-value)]
      (doseq [neighbor neighbors-to-expand]
        (grid/set-point local-grid neighbor (:value neighbor)))
      neighbors-to-expand)))

; --- ORIGINAL VARIANT
(defnp expand-original [src dst local-grid params]
  "Try to find a path from `src` to `dst` through `local-grid`.
  Updates `local-grid` and returns true if the destination was reached. (There
  might be multiple paths from src to dst in the grid.)"
  (grid/set-point local-grid src 0)
  (grid/set-point local-grid dst :empty)
  (let [queue (LinkedList.)]
    (.add queue src)
    (loop []
      ;(log "expansion queue" queue)
      (if (empty? queue)
        false ; no path
        (let [current (.pop queue)]
          (if (coordinate/equal? current dst)
            true ; dst reached, local-grid updated
            (let [new-points (expand-point local-grid current params)]
              (.addAll queue new-points)
              (recur))))))))
; --- END ORIGINAL VARIANT

; --- PBFS VARIANT
(defmacro for-all [seq-exprs body-expr]
  `(doall
    (for ~seq-exprs
      ~body-expr)))

(defmacro parallel-for-all [seq-exprs body-expr]
  `(doall
    (map deref
      (doall
        (for ~seq-exprs
          (future ~body-expr))))))

(defn new-bag
  ([] (ConcurrentHashMap/newKeySet))
  ([init] (let [bag (ConcurrentHashMap/newKeySet)] (.addAll bag init) bag)))

(defnp expand-partition [points new-bag dst local-grid found? params]
  (loop [points points]
    (if-let [current (first points)]
      (if (coordinate/equal? current dst)
        (ref-set found? true)
        (do
          (.addAll new-bag (expand-point local-grid current params))
          (recur (rest points)))))))

(defnp expand-step [bag dst local-grid found? params]
  (let [new-bag (new-bag)]
    (if (< (count bag) 25)
      ; process sequentially if there are < 25 points in the bag
      (expand-partition bag new-bag dst local-grid found? params)
      ; divide bag in (:n-partitions params) (default 4) partitions, but
      ; partition size should be at least 20
      (let [partition-size (max (int (/ (count bag) (:n-partitions params))) 20)
            partitions     (partition partition-size partition-size (list) bag)]
        (p :expand-partitions
          (parallel-for-all [partition partitions]
            (p :expand-partition
              (expand-partition partition new-bag dst local-grid found? params))))))
    new-bag))

(defnp expand-bag [local-grid src dst params]
  "Returns true if a path from src to dst was found, false if no path was
  found. Modifies local-grid in both cases.

  This is a version that uses 'bags', inspired by [1], [2], and [3].

  [1] Y. Zhang and E. A. Hansen. Parallel Breadth-First Heuristic Search on a
  Shared-Memory Architecture. In AAAI Workshop on Heuristic Search, Memory-Based
  Heuristics and Their Applications, 2006.
  [2] C. E. Leierson and T. B. Schardl. A Work-Efficient Parallel Breadth-First
  Search Algorithm (or How to Cope with the Nondeterminism of Reducers). In
  SPAA'10, 2010.
  [3] 'High Performance Computing' class on Udacity.
  https://www.youtube.com/watch?v=pxOL-R7gUiQ and
  https://www.youtube.com/watch?v=M4HSekx-8XA"
  (let [found? (ref false :resolve (fn [o p c] (or p c)))]
    (loop [bag (new-bag [src])]
      (let [new-bag (expand-step bag dst local-grid found? params)]
        (cond
          @found?          true
          (empty? new-bag) false
          :else            (recur new-bag))))))

(defnp expand-pbfs [src dst local-grid params]
  "Try to find a path from `src` to `dst` through `local-grid`.
  Updates `local-grid` and returns true if the destination was reached. (There
  might be multiple paths from src to dst in the grid.)"
  (grid/set-point local-grid src 0)
  (grid/set-point local-grid dst :empty)
  (expand-bag local-grid src dst params))
; --- END PBFS VARIANT

(defn next-steps [local-grid current-step bend-cost]
  "All possible next steps after the current one, and their cost.
  Returns list of elements of the format:
  `{:step {:point next-point :direction dir} :cost 123}`"
  (->>
    [:x-pos :x-neg :y-pos :y-neg :z-pos :z-neg]
    (map
      (fn [dir]
        (let [point (coordinate/step-to dir (:point current-step))]
          (if (and (grid/is-point-valid? local-grid point)
                   (not (= (grid/get-point local-grid point) :empty))
                   (not (= (grid/get-point local-grid point) :full)))
            (let [bending? (not= dir (:direction current-step))
                  b-cost   (if bending? bend-cost 0)
                  cost     (+ (grid/get-point local-grid point) b-cost)]
              {:step {:point point :direction dir} :cost cost})
            nil))))
    (filter identity))) ; filter out nil

(defn find-cheapest-step [local-grid current-step params]
  "Returns least costly step amongst possible next steps.
  A step is of the form `{:point next-point :direction dir}` where `next-point`
  is a neighbor of `current` and `dir` is e.g. `:x-pos`."
  ; first, try with bend cost
  (let [current-val
          (grid/get-point local-grid (:point current-step))
        steps
          (next-steps local-grid current-step (:bend-cost params))
        cheapest
          (first (sort-by :cost steps))]
    (if (and (not (empty? steps)) (<= (:cost cheapest) current-val))
      (:step cheapest)
      ; if none found, try without bend cost
      (let [steps    (next-steps local-grid current-step 0)
            cheapest (first (sort-by :cost steps))]
        (if (and (not (empty? steps)) (<= (:cost cheapest) current-val))
          (:step cheapest)
          (log "no cheap step found"))))))

(defnp traceback [local-grid dst params]
  "Go back from dst to src, along an optimal path, and mark these cells as
  filled in the local grid. "
  (loop [current-step {:point dst :direction :zero}
         path         (list)]
    (let [current-point (:point current-step)]
      (if (= (grid/get-point local-grid current-point) 0)
        ; current-point = source: we're done
        (cons current-point path)
        ; find next point along cheapest step
        (if-let [next-step (find-cheapest-step local-grid current-step params)]
          (do
            (grid/set-point local-grid current-point :full)
            (recur next-step (cons current-point path)))
          (log "traceback failed"))))))

(defnp find-work [queue]
  "In a transaction, pops element of queue and returns it, or returns nil
  if queue is empty."
  (let [work
          (dosync
            (if (empty? @queue)
              nil
              (let [top (first @queue)]
                (alter queue rest)
                top)))]
    (log "found work" work)
    work))

(defnp find-path [[src dst] shared-grid params]
  "Tries to find a path. Returns path if one was found, nil otherwise.
  A path is a vector of points."
  (dosync-tracked
    (let [local-grid (p :find-path-1-copy (grid/copy-local shared-grid))
          reachable?
            (p :find-path-2-expand
              (case (:variant params)
                :original (expand-original src dst local-grid params)
                          (expand-pbfs src dst local-grid params)))]
      (if reachable?
        (let [path (p :find-path-3-traceback (traceback local-grid dst params))]
          (when path
            (p :find-path-4-add-path
              (grid/add-path shared-grid path))) ; may fail and cause rollback
          path)
        (log "expansion failed")))))

(defnp solve [params maze paths-per-thread]
  "Solve maze, append found paths to `paths-per-thread`."
  (let [my-paths
          ; find paths until no work left
          (loop [my-paths []]
            (if-let [work (find-work (:work-queue maze))]      ; find-work = tx
              (let [path (find-path work (:grid maze) params)] ; find-path = tx
                (log "found path" path)
                (if path
                  (recur (conj my-paths path))
                  (recur my-paths)))
              my-paths))]
    ; add found paths to shared list of list of paths
    ; Note: in Clojure, it would make more sense to return my-paths and let
    ; the caller merge them (not using transactions). However, in the C++
    ; version threads can't return anything so the results need to be written to
    ; a shared variable in this manner.
    (dosync
      (alter paths-per-thread conj my-paths))))

;(defn solve-with-serial-printing [p m ps]
;  (let [writer (StringWriter.)
;        result (binding [*out* writer]
;                 (solve p m ps))]
;    (println (.toString writer))
;    result))
