(ns router
  (:require [coordinate]))

; Not needed
;(defn alloc [x-cost y-cost z-cost bend-cost]
;  "Returns a list of router paramaters."
;  {:x-cost    x-cost
;   :y-cost    y-cost
;   :z-cost    z-cost
;   :bend-cost bend-cost})

(def log println)

(defn- expand-point [local-grid {x :x y :y z :z :as point} params]
  "Expands one step past `point`, i.e. to the neighbors of `point`.
  A neighbor is still to be expanded if it not filled yet, and either:
  1. has no path to it yet (it is empty), or
  2. has a longer path to it (its current value > value of `point` + cost to go
     to it).
  This function returns {:grid updated-grid :new-points expanded-neighbors}"
  (let [{:keys [x-cost y-cost z-cost]}
          params
        value
          (grid/get-point local-grid point)
        all-neighbors
          [{:x (+ x 1) :y    y    :z    z    :cost x-cost}
           {:x (- x 1) :y    y    :z    z    :cost x-cost}
           {:x    x    :y (+ y 1) :z    z    :cost y-cost}
           {:x    x    :y (- y 1) :z    z    :cost y-cost}
           {:x    x    :y    y    :z (+ z 1) :cost z-cost}
           {:x    x    :y    y    :z (- z 1) :cost z-cost}]
        existing-neighbors
          (filter #(grid/is-point-valid? local-grid %) all-neighbors)
        neighbors-to-expand
          (filter
            (fn [p]
              (and
                (not= (grid/get-point local-grid p) :full)
                (or
                  (= (grid/get-point local-grid p) :empty)
                  (<
                    (+ value (:cost p))
                    (grid/get-point local-grid p)))))
            existing-neighbors)
        updated-grid
          (reduce
            (fn [local-grid p]
              (grid/set-point local-grid p (+ value (:cost p))))
            local-grid
            neighbors-to-expand)]
    {:grid updated-grid :new-points neighbors-to-expand}))

(defn expand [src dst local-grid-initial params]
  "Try to find a path from `src` to `dst` through `local-grid`.
  Returns `{:grid grid :reachable found}`, where `grid` is the updated grid and
  `found` is true if the destination was reached. (There might be multiple
  paths from src to dst.)"
  (log "src" src)
  (log "dst" dst)
  (loop [queue
          ; start at source
          [src]
        local-grid
          (-> local-grid-initial
            (grid/set-point src 0)        ; src = 0
            (grid/set-point dst :empty))] ; dst = empty
    (log "queue" queue)
    (if (empty? queue)
      {:grid local-grid :reachable false}
      (let [current (first queue)]
        (if (coordinate/equal? current dst)
          {:grid local-grid :reachable true}
          (let [{updated-grid :grid new-points :new-points}
                  (expand-point local-grid current params)]
            (recur
              (vec (concat (rest queue) new-points))
              updated-grid)))))))

(defn- next-steps [shared-grid local-grid current-step bend-cost]
  "All possible next steps after the current one, and their cost.
  Returns list of elements of the format:
  `{:step {:point next-point :direction dir} :cost 123}`"
  (->>
    [:x-pos :x-neg :y-pos :y-neg :z-pos :z-neg]
    (map
      (fn [dir]
        (let [point (coordinate/step-to dir (:point current-step))]
          (if (and (grid/is-point-valid? shared-grid point)
                   (not (= (grid/get-point local-grid point) :empty))
                   (not (= @(grid/get-point shared-grid point) :full)))
            (let [bending? (not= dir (:direction current-step))
                  b-cost   (if bending? bend-cost 0)
                  cost     (+ (grid/get-point local-grid point) b-cost)]
              {:step {:point point :direction dir} :cost cost})
            nil))))
    (filter identity))) ; filter out nil

(defn- find-cheapest-step [shared-grid local-grid current-step params]
  "Returns least costly step amongst possible next steps.
  A step is of the form `{:point next-point :direction dir}` where `next-point`
  is a neighbor of `current` and `dir` is e.g. `:x-pos`."
  ; first, try with bend cost
  (let [current-val
          (grid/get-point local-grid (:point current-step))
        steps
          (next-steps shared-grid local-grid current-step (:bend-cost params))
        cheapest
          (first (sort-by :cost steps))]
    (if (<= (:cost cheapest) current-val)
      (:step cheapest)
      ; if none found, try without bend cost
      (let [steps    (next-steps shared-grid local-grid current-step 0)
            cheapest (first (sort-by :cost steps))]
        (if (<= (:cost cheapest) current-val)
          (:step cheapest)
          (println "no cheap step found (cannot happen)"))))))

(defn traceback [shared-grid local-grid dst params]
  "Go back from dst to src, along an optimal path, and mark these cells as
  filled in the shared and local grid. "
  (loop [current-step {:point dst :direction :zero}
         path         (list)]
    (let [current-point (:point current-step)]
      ; current point full in shared grid
      ; Note: in the C++ version, the local grid is set here, and the shared
      ; grid is set in solve. We do one set, to the shared grid, here.
      (ref-set (grid/get-point shared-grid current-point) :full)
      (if (= (grid/get-point local-grid current-point) 0)
        ; current-point = source: we're done
        (cons current-point path)
        ; find next point along cheapest step
        (if-let [next-step (find-cheapest-step shared-grid local-grid
                             current-step params)]
          (recur next-step (cons current-point path))
          (log "traceback failed (cannot happen)"))))))

(defn- find-work [queue]
  "In a transaction, pops element of queue and returns it, or returns nil
  if queue is empty."
  (dosync
    (if (empty? @queue)
      nil
      (let [top (first @queue)]
        (log "found work" top)
        (alter queue pop)
        top))))

(defn- find-path [[src dst] shared-grid params]
  "Tries to find a path. Returns path if one was found, nil otherwise.
  A path is a vector of points."
  (dosync
    (let [{reachable? :reachable local-grid :grid}
            (expand src dst (grid/copy shared-grid) params)]
      (if reachable?
        (traceback shared-grid local-grid dst params)
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
