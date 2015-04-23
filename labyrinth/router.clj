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

(defn- next-steps [global-grid local-grid current-step bend-cost]
  "All possible next steps after the current one, and their cost.
  Returns list of elements of the format:
  `{:step {:point next-point :direction dir} :cost 123}`"
  (->>
    [:x-pos :x-neg :y-pos :y-neg :z-pos :z-neg]
    (map
      (fn [dir]
        (let [point (coordinate/step-to dir (:point current-step))]
          (if (and (grid/is-point-valid? global-grid point)
                   (not (= (grid/get-point local-grid point) :empty))
                   (not (= @(grid/get-point global-grid point) :full)))
            (let [bending?  (not= dir (:direction current-step))
                  b-cost    (if bending? bend-cost 0)
                  cost      (+ (grid/get-point local-grid point) b-cost)]
              {:step {:point point :direction dir} :cost cost})
            nil))))
    (filter identity))) ; filter nil

(defn- find-cheapest-step [global-grid local-grid current-step params]
  "Returns least costly step amongst possible next steps.
  A step is of the form `{:point next-point :direction dir}` where `next-point`
  is a neighbor of `current` and `dir` is e.g. `:x-pos`."
  ; First, try with bend cost
  (let [current  (grid/get-point local-grid (:point current-step))
        steps    (next-steps global-grid local-grid current-step (:bend-cost params))
        cheapest (first (sort-by :cost steps))]
    (if (<= (:cost cheapest) current)
      (:step cheapest)
      ; If none found, try without bend cost
      (let [steps    (next-steps global-grid local-grid current-step 0)
            cheapest (first (sort-by :cost steps))]
        (if (<= (:cost cheapest) current)
          (:step cheapest)
          (println "No cheap step found (cannot happen)."))))))

(defn traceback [global-grid local-grid dst params]
  "Go back from dst to src, along an optimal path, and mark these cells as
  filled in the global and local grid. "
  (loop [current {:point dst :direction :zero}
         path    (list)]
    (ref-set (grid/get-point global-grid (:point current)) :full)
    (if (= (grid/get-point local-grid (:point current)) 0)
      (cons (:point current) path)
      (let [next-step (find-cheapest-step global-grid local-grid current params)]
        (if next-step
          (recur next-step (cons (:point current) path))
          nil)))))

(defn- find-work [queue]
  "In a transaction, pops element of queue and returns it, or returns nil
  if queue is empty."
  (dosync
    (if (empty? @queue)
      nil
      (let [top (first @queue)]
        (log "found work" top)
        (log queue)
        (alter queue pop)
        top))))

(defn- find-path [[src dst] global-grid params]
  "Tries to find a path. Returns path if one was found, nil otherwise.
  A path is a vector of points."
  (dosync
    (let [{reachable? :reachable local-grid :grid}
            (expand src dst (grid/copy global-grid) params)]
      (if reachable?
        (let [path (traceback global-grid local-grid dst params)]
          (if path
            (do
              (grid/add-path global-grid path) ; update global grid
              path)
            (log "traceback failed"))) ; traceback failed
        (log "expansion failed"))))) ; expansion failed

(defn solve [params maze list-of-paths]
  "Solve maze, append found paths to `list-of-paths`."
  (let [work-queue  (:work-queue maze)
        global-grid (:grid maze)
        my-paths
          ; find paths until no work left
          (loop [my-paths []]
            (let [work (find-work work-queue)]
              (if work
                (let [path (find-path work global-grid params)]
                  (log "found path" path)
                  (if path
                    (recur (conj my-paths path))
                    (recur my-paths)))
                my-paths)))]
    ; add found paths to global list of list of paths
    (dosync
      (alter list-of-paths conj my-paths))))
