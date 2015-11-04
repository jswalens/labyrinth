(ns labyrinth.main
  (:gen-class)
  (:require [labyrinth.maze :as maze]
            [labyrinth.router :as router]
            [labyrinth.util :refer [str->int time print-tx-stats]]))

(def default-params
  {:bend-cost  1
   :n-threads  1
   :x-cost     1
   :y-cost     1
   :z-cost     2
   :input-file "inputs/random-x32-y32-z3-n64.txt"
   :print      false})

(def usage
"Usage: ./labyrinth [options]

Options:                            (defaults)

    b <INT>    [b]end cost          (1)
    i <FILE>   [i]nput file name    (labyrinth/inputs/random-x32-y32-z3-n96.txt)
    p          [p]rint routed maze  (false)
    t <UINT>   Number of [t]hreads  (1)
    x <UINT>   [x] movement cost    (1)
    y <UINT>   [y] movement cost    (1)
    z <UINT>   [z] movement cost    (2)")

(def log println)

(defn parse-args [args]
  "Parse the arguments."
  (let [process-argument-name
         (fn [res arg]
            (if (map? res)
              ; Trick: for parameter names that expect a value (e.g. -i), we
              ; return a function. In the next iteration, this will be filled in
              ; by calling it with the parameter value.
              (case (.substring arg 1)
                "b" #(assoc res :bend-cost (str->int %))
                "t" #(assoc res :n-threads (str->int %))
                "x" #(assoc res :x-cost (str->int %))
                "y" #(assoc res :y-cost (str->int %))
                "z" #(assoc res :z-cost (str->int %))
                "i" #(assoc res :input-file %)
                "p" (assoc res :print true)
                    (assoc res :arg-error true))
              (assoc default-params :arg-error true)))
        process-argument-value
          (fn [res arg]
            ; Call previous result, assuming it's a function that sets the
            ; right parameter. Otherwise ignore and indicate error.
            (if (fn? res)
              (res arg)
              (assoc res :arg-error true)))
        result
          (reduce
            (fn [res arg]
              (if (.startsWith arg "-")
                (process-argument-name res arg)
                (process-argument-value res arg)))
            default-params args)]
      (if (map? result)
        result
        (assoc default-params :arg-error true))))

(defn -main [& args]
  "Main function. `args` should be a list of command line arguments."
  (let [params (parse-args args)]
    (if (or (:arg-error params) (nil? params))
      (do (println "Error parsing arguments")
          (println "Params:" params)
          (println usage))
      (let [maze
              (maze/read (:input-file params))
            paths-per-thread
              (ref [])
            results
              (time ; time everything
                (doall
                  (pmap
                    (fn [_]
                      (time ; timer per thread
                        (router/solve params maze paths-per-thread)))
                    (range (:n-threads params)))))]
        ;(log "Paths (per thread):" @paths-per-thread)
        (println "Paths routed    =" (reduce + (map count @paths-per-thread)))
        (println "Elapsed time    =" (:time results) "milliseconds")
        (println "Time per thread:")
        (doseq [t (:result results)]
          (println " " (:time t) "milliseconds"))
        (print-tx-stats)
        ; verification of paths, also prints grid if asked to
        ; Note: (apply concat ...) flattens once, i.e. it turns the list of
        ; list of paths into a single list of paths (but each path is still a
        ; list of points)
        (if (maze/check-paths maze (apply concat @paths-per-thread)
              (:print params))
          (println "Verification passed.")
          (println "Verification FAILED!"))
        (shutdown-agents)))))

; To run manually:
;(main *command-line-args*)
