(ns labyrinth.router
  (:require [labyrinth.coordinate :as coordinate]
            [labyrinth.grid :as grid]
            [labyrinth.util :refer [dosync-tracked]])
  (:import [java.io StringWriter]
           [java.util LinkedList]))

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

(defn score [local-grid current next params]
  (let [current-value @(grid/get-point local-grid current)
        directional-costs
          (for [[dir cost] [[:x :x-cost] [:y :y-cost] [:z :z-cost]]]
            (if (not= (dir current) (dir next)) ; changed in this direction
              (cost params)
              0))]
    (+ current-value
       (reduce + directional-costs)
       (grid/get-point-cost local-grid next))))

(defn expand-point [local-grid {x :x y :y z :z :as point} params]
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
                (let [nb-current-value @(grid/get-point local-grid neighbor)]
                  (and
                    (not= nb-current-value :full)
                    (or
                      (= nb-current-value :empty)
                      (< (:value neighbor) nb-current-value)))))
              neighbors-with-value)]
      (doseq [neighbor neighbors-to-expand]
        (ref-set (grid/get-point local-grid neighbor) (:value neighbor)))
      neighbors-to-expand)))

(defn min-grid-point [a b]
  (cond
    (= a :full)  :full
    (= b :full)  :full
    (= a :empty) b
    (= b :empty) a
    :else        (min a b)))

(defn expand [src dst local-grid-initial params]
  "Try to find a path from `src` to `dst` through `local-grid-initial`.
  Returns `{:grid grid :reachable found}`, where `grid` is the updated grid and
  `found` is true if the destination was reached. (There might be multiple
  paths from src to dst in the grid.)"
  (let [queue (LinkedList.)
        local-grid
          (-> local-grid-initial
            (grid/set-point src 0)        ; src = 0
            (grid/set-point dst :empty)   ; dst = empty
            (grid/grid-map
              #(ref % :resolve (fn [o p c] (min-grid-point p c)))))]
              ; make each cell a ref with custom resolve function min-grid-point
    (.add queue src)
    (loop []
      ;(log "expansion queue" queue)
      (if (empty? queue)
        {:grid (grid/grid-map local-grid deref) :reachable false} ; no path
        (let [current (.pop queue)]
          (if (coordinate/equal? current dst)
            {:grid (grid/grid-map local-grid deref) :reachable true} ; dst reached
            (let [new-points (expand-point local-grid current params)]
              (.addAll queue new-points)
              (recur))))))))

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

(defn traceback [local-grid dst params]
  "Go back from dst to src, along an optimal path, and mark these cells as
  filled in the local grid. "
  (loop [current-step {:point dst :direction :zero}
         grid         local-grid
         path         (list)]
    (let [current-point (:point current-step)]
      (if (= (grid/get-point local-grid current-point) 0)
        ; current-point = source: we're done
        (cons current-point path)
        ; find next point along cheapest step
        (if-let [next-step (find-cheapest-step local-grid current-step params)]
          (recur next-step (grid/set-point local-grid current-point :full)
            (cons current-point path))
          (log "traceback failed"))))))

(defn find-work [queue]
  "In a transaction, pops element of queue and returns it, or returns nil
  if queue is empty."
  (let [work
          (dosync
            (if (empty? @queue)
              nil
              (let [top (first @queue)]
                (alter queue pop)
                top)))]
    (log "found work" work)
    work))

(defn find-path [[src dst] shared-grid params]
  "Tries to find a path. Returns path if one was found, nil otherwise.
  A path is a vector of points."
  (dosync-tracked
    (let [{reachable? :reachable local-grid :grid}
            (expand src dst (grid/copy shared-grid) params)]
      (if reachable?
        (let [path (traceback local-grid dst params)]
          (when path
            (grid/add-path shared-grid path)) ; may fail and cause rollback
          path)
        (log "expansion failed")))))

(defn solve [params maze paths-per-thread]
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
