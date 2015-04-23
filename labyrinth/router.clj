(ns router
  (:require [coordinate]))

; Not needed
;(defn alloc [x-cost y-cost z-cost bend-cost]
;  "Returns a list of router paramaters."
;  {:x-cost    x-cost
;   :y-cost    y-cost
;   :z-cost    z-cost
;   :bend-cost bend-cost})

(defn- expand-point [grid {x :x y :y z :z :as point} params]
  "Expands one step past `point`, i.e. to the neighbors of `point`.
  A neighbor is still to be expanded if it not filled yet, and either:
  1. has no path to it yet (it is empty), or
  2. has a longer path to it (its current value > value of `point` + cost to go
     to it).
  This function returns {:grid updated-grid :new-points expanded-neighbors}"
  (let [{:keys [x-cost y-cost z-cost]}
          params
        value
          (grid/get-point grid point)
        all-neighbors
          [{:x (+ x 1) :y    y    :z    z    :cost x-cost}
           {:x (- x 1) :y    y    :z    z    :cost x-cost}
           {:x    x    :y (+ y 1) :z    z    :cost y-cost}
           {:x    x    :y (- y 1) :z    z    :cost y-cost}
           {:x    x    :y    y    :z (+ z 1) :cost z-cost}
           {:x    x    :y    y    :z (- z 1) :cost z-cost}]
        existing-neighbors
          (filter #(grid/is-point-valid? grid %) all-neighbors)
        neighbors-to-expand
          (filter
            (fn [p]
              (and
                (not= (grid/get-point grid p) :full)
                (or
                  (= (grid/get-point grid p) :empty)
                  (<
                    (+ value (:cost p))
                    (grid/get-point grid p)))))
            existing-neighbors)
        updated-grid
          (reduce
            (fn [grid p]
              (grid/set-point grid p (+ value (:cost p))))
            grid
            neighbors-to-expand)]
    {:grid updated-grid :new-points neighbors-to-expand}))

(defn expand [src dst my-grid params]
  "Try to find a path from `src` to `dst` through `my-grid`.
  Returns `{:grid grid :reachable found}`, where `grid` is the updated grid and
  `found` is true if the destination was reached. (There might be multiple
  paths from src to dst.)"
  (loop [queue
          ; start at source
          [src]
        grid
          (-> my-grid
            ; src = 0
            (assoc-in [:points (grid/get-point-index my-grid src)] 0)
            ; dst = empty
            (assoc-in [:points (grid/get-point-index my-grid dst)] :empty))]
    (if (empty? queue)
      {:grid grid :reachable false}
      (let [current (first queue)]
        (if (coordinate/equal? current dst)
          {:grid grid :reachable true}
          (let [{updated-grid :grid new-points :new-points}
                  (expand-point grid current params)]
            (recur
              (vec (concat (rest queue) new-points))
              updated-grid)))))))

(defn- next-steps [grid my-grid current-step bend-cost]
  "All possible next steps after the current one, and their cost.
  Returns list of elements of the format:
  `{:step {:point next-point :direction dir} :cost 123}`"
  (->>
    [:x-pos :x-neg :y-pos :y-neg :z-pos :z-neg]
    (map
      (fn [dir]
        (let [point (coordinate/step-to dir (:point current-step))]
          (if (and (grid/is-point-valid? grid point)
                   (not (= (grid/get-point my-grid point) :empty))
                   (not (= @(grid/get-point grid point) :full)))
            (let [bending?  (not= dir (:direction current-step))
                  b-cost    (if bending? bend-cost 0)
                  cost      (+ (grid/get-point my-grid point) b-cost)]
              {:step {:point point :direction dir} :cost cost})
            nil))))
    (filter identity))) ; filter nil

(defn- find-cheapest-step [grid my-grid current-step params]
  "Returns least costly step amongst possible next steps.
  A step is of the form `{:point next-point :direction dir}` where `next-point`
  is a neighbor of `current` and `dir` is e.g. `:x-pos`."
  ; First, try with bend cost
  (let [steps    (next-steps grid my-grid current-step (:bend-cost params))
        cheapest (first (sort-by :cost steps))]
    (if (<= (:cost cheapest) (grid/get-point my-grid (:point current-step)))
      (:step cheapest)
      ; If none found, try without bend cost
      (let [steps    (next-steps grid my-grid current-step 0)
            cheapest (first (sort-by :cost steps))]
        (if (<= (:cost cheapest) (grid/get-point my-grid (:point current-step)))
          (:step cheapest)
          (println "No cheap step found (cannot happen)."))))))

(defn traceback [grid my-grid dst params]
  "Go back from dst to src, along an optimal path, and mark these cells as
  filled in the grid. "
  (loop [current {:point dst :direction :zero}
         path    (list)]
    (ref-set (grid/get-point grid (:point current)) :full)
    (if (= (grid/get-point-index my-grid (:point current)) 0)
      (cons (:point current) path)
      (let [next-step (find-cheapest-step grid my-grid current params)]
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
        (println "found work" top)
        (println queue)
        (alter queue pop)
        top))))

(defn- find-path [[src dst] grid params]
  "Tries to find a path. Returns path if one was found, nil otherwise.
  A path is a vector of points."
  (dosync
    (let [{reachable? :reachable my-grid :grid}
            (expand src dst (grid/copy grid) params)]
      (if reachable?
        (let [path (traceback grid my-grid dst params)]
          (if path
            (do
              (grid/add-path grid path) ; update global grid
              path)
            nil)) ; traceback failed
        nil)))) ; expansion failed

(defn solve [params maze list-of-paths]
  "Solve maze, append found paths to `list-of-paths`."
  (let [work-queue (:work-queue maze)
        grid       (:grid maze)
        my-paths
          ; find paths until no work left
          (loop [my-paths []]
            (let [work (find-work work-queue)]
              (if work
                (let [path (find-path work grid params)]
                  (if path
                    (recur (conj my-paths path))
                    (recur my-paths)))
                my-paths)))]
    ; add found paths to global list of list of paths
    (dosync
      (alter list-of-paths conj my-paths))))
