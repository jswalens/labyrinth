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
        (if (= current dst) ; XXX: does = work here?
          {:grid grid :reachable true}
          (let [{updated-grid :grid new-points :new-points}
                  (expand-point grid current params)]
            (recur
              (concat (pop queue) new-points)
              updated-grid)))))))

(defn- next-steps [grid my-grid current-step params]
  "All possible next steps after the current one, and their cost.
  Returns list of elements of the format:
  `{:step {:point next-point :direction dir} :cost 123}`"
  (->>
    [{:direction :x-pos :bend-cost true}
     {:direction :x-neg :bend-cost true}
     {:direction :y-pos :bend-cost true}
     {:direction :y-neg :bend-cost true}
     {:direction :z-pos :bend-cost true}
     {:direction :z-neg :bend-cost true}
     {:direction :x-pos :bend-cost false}
     {:direction :x-neg :bend-cost false}
     {:direction :y-pos :bend-cost false}
     {:direction :y-neg :bend-cost false}
     {:direction :z-pos :bend-cost false}
     {:direction :z-neg :bend-cost false}]
    (map
      (fn [{dir :direction bend-cost? :bend-cost}]
        (let [point (coordinate/step-to dir (:point current-step))]
          (if (and (grid/is-point-valid? grid point)
                   (not (= (grid/get-point my-grid point) :empty))
                   (not (= (grid/get-point grid point) :full)))
            (let [bending? (not= dir (:direction current-step))
                  bend-cost (if (and bend-cost? bending?) (:bend-cost params) 0)
                  cost (+ (grid/get-point my-grid point) bend-cost)]
              {:step {:point point :direction dir} :cost cost})
            nil))))
    (filter identity))) ; filter nil

(defn- find-cheapest-step [grid my-grid current-step params]
  "Returns least costly step amongst possible next steps.
  A step is of the form `{:point next-point :direction dir}` where `next-point`
  is a neighbor of `current` and `dir` is e.g. `:x-pos`."
  ; XXX: In contrast to the original version, we don't check whether the found
  ; step is cheaper than staying in place, as this normally should never be the
  ; case.
  (:step (first (sort-by :cost (next-steps grid my-grid current-step params)))))

(defn traceback [grid my-grid dst params]
  "Go back from dst to src, along an optimal path, and mark these cells as
  filled in the grid. "
  (loop [current {:point dst :direction :zero}
         path    (list)]
    (ref-set current :full)
    (if (= (grid/get-point-index my-grid (:point current)) 0)
      (cons (:point current) path)
      (recur
        (find-cheapest-step grid my-grid current params)
        (cons (:point current) path)))))

(defn- find-work [queue]
  "In a transaction, fetches top of queue, or returns nil if queue is empty."
  (dosync
    (if (empty? @queue)
      nil
      (first @queue))))

(defn- find-path [[src dst] grid params]
  "Tries to find a path. Returns path if one was found, nil otherwise.
  A path is a vector of XXX coordinates/points? XXX"
  (dosync
    (let [{reachable? :reachable my-grid :grid}
            (expand src dst (grid/copy grid) params)]
      (if reachable?
        (let [path (traceback grid my-grid dst params)]
          (if path
            (do
              (grid/add-path path) ; update grid to mark path as taken
              path)
            nil)) ; traceback failed
        nil)))) ; expansion failed

(defn solve [params maze paths]
  "Solve maze, append found paths to `paths`."
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
    ; add found paths to global list of (list ofXXX) paths
    (dosync
      (alter paths conj my-paths))))
