(ns router)

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
                (!= (grid/get-point grid p) :full)
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
  Returns `{:grid grid :path found}`, where `grid` is the updated grid and
  `found` is true if a path was found."
  (loop [queue
          ; start at source
          [src]
        grid
          (-> my-grid
            ; src = 0
            (assoc-in [:points (grid/get-point-index src)] 0)
            ; dst = empty
            (assoc-in [:points (grid/get-point-index dst)] :empty))]
    (if (empty? queue)
      {:grid grid :path false}
      (let [current (first queue)]
        (if (= current dst) ; XXX: does = work here?
          {:grid grid :path true}
          (let [{updated-grid :grid new-points :new-points}
                  (expand-point grid current params)]
            (recur
              (concat (pop queue) new-points)
              updated-grid)))))))

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
    (let [my-grid ...copy grid...]
      (if (expand src dst my-grid params)
        (let [path (traceback ...)]
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
