(ns labyrinth.main
  (:gen-class)
  (:refer-clojure :exclude [time])
  (:require [labyrinth.maze :as maze]
            [labyrinth.router :as router]
            [labyrinth.util :refer [str->int time print-tx-stats]]))

(def default-args
  {:variant    :pbfs
   :bend-cost  1
   :n-threads  1
   :n-partitions 4
   :x-cost     1
   :y-cost     1
   :z-cost     2
   :input-file "inputs/random-x32-y32-z3-n64.txt"
   :print      false})

(def usage
"Usage: lein run -- [options]

Options:                    values        default
  v  [v]ariant              original|pbfs (pbfs)
  b  [b]end cost            <INT>         (1)
  i  [i]nput file name      <FILE>        (labyrinth/inputs/random-x32-y32-z3-n96.txt)
  p  [p]rint routed maze                  (false)
  t  Number of [t]hreads    <UINT>        (1)
  x  [x] movement cost      <UINT>        (1)
  y  [y] movement cost      <UINT>        (1)
  z  [z] movement cost      <UINT>        (2)

Only for pbfs variant:
  a  Number of p[a]rtitions <UINT>        (4)")

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
                "v" #(assoc res :variant
                       (case %
                         "original" :original
                                    :pbfs))
                "b" #(assoc res :bend-cost (str->int %))
                "t" #(assoc res :n-threads (str->int %))
                "a" #(assoc res :n-partitions (str->int %))
                "x" #(assoc res :x-cost (str->int %))
                "y" #(assoc res :y-cost (str->int %))
                "z" #(assoc res :z-cost (str->int %))
                "i" #(assoc res :input-file %)
                "p" (assoc res :print true)
                    (assoc res :arg-error true))
              (assoc default-args :arg-error true)))
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
            default-args args)]
      (if (map? result)
        result
        (assoc default-args :arg-error true))))

(defn -main [& args]
  "Main function. `args` should be a list of command line arguments."
  (let [params (parse-args args)]
    (when (or (:arg-error params) (nil? params))
      (println "Error parsing arguments")
      (println "Params:" params)
      (println usage)
      (System/exit 1))
    (when-not (.exists (clojure.java.io/as-file (:input-file params)))
      (println "The input file" (:input-file params) "does not exist.")
      (println "Specify an input file using the command line parameter -i.")
      (System/exit 2))
    (println "Variant         =" (:variant params))
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
      (shutdown-agents))))

; To run manually:
;(main *command-line-args*)
