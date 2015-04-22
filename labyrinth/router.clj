(ns router)

; Not needed
;(defn alloc [x-cost y-cost z-cost bend-cost]
;  "Returns a list of router paramaters."
;  {:x-cost    x-cost
;   :y-cost    y-cost
;   :z-cost    z-cost
;   :bend-cost bend-cost})

(defn- find-work [queue]
  "In a transaction, fetches top of queue, or returns nil if queue is empty."
  (dosync
    (if (empty? @queue)
      nil
      (first @queue))))

(defn- find-path [[src dst] grid]
  "Tries to find a path. Returns path if one was found, nil otherwise.
  A path is a vector of XXX coordinates/points? XXX"
  (dosync
    (let [my-grid ...copy grid...]
      (if (expand ...)
        (let [path (traceback ...)]
          (if path
            (do
              ;(grid/add-path path) update grid to mark path as taken
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
                (let [path (find-path work grid)]
                  (if path
                    (recur (conj my-paths path))
                    (recur my-paths)))
                my-paths)))]
    ; add found paths to global list of (list ofXXX) paths
    (dosync
      (alter paths conj my-paths))))
