(ns labyrinth.main
  (:gen-class)
  (:refer-clojure :exclude [time])
  (:require [labyrinth.maze :as maze]
            [labyrinth.router :as router]
            [labyrinth.util :refer [str->int time print-tx-stats]]
            [taoensso.tufte :as tufte :refer [profiled p format-pstats]]))

(defmacro parallel-for-all [seq-exprs body-expr]
  `(doall
    (map deref
      (doall
        (for ~seq-exprs
          (future ~body-expr))))))

(def default-args
  {:input-file "inputs/random-x32-y32-z3-n64.txt"
   :variant    :pbfs
   :n-threads  1
   :n-partitions 4
   :x-cost     20
   :y-cost     20
   :z-cost     60
   :bend-cost  1
   :print      false
   :profile    false})

(def usage
"Usage: lein run -- [options]

Options:                    values        default
  i  [i]nput file name      <FILE>        (labyrinth/inputs/random-x32-y32-z3-n96.txt)
  v  [v]ariant              original|pbfs (pbfs)
  t  number of [t]hreads    <UINT>        (1)
  x  [x] movement cost      <UINT>        (1)
  y  [y] movement cost      <UINT>        (1)
  z  [z] movement cost      <UINT>        (2)
  b  [b]end cost            <INT>         (1)
  p  [p]rint routed maze                  (false)
  m  enable profiling                     (false)

Only for pbfs variant:
  a  number of p[a]rtitions <UINT>        (4)")

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
                "i" #(assoc res :input-file %)
                "v" #(assoc res :variant
                       (case %
                         "original" :original
                                    :pbfs))
                "t" #(assoc res :n-threads (str->int %))
                "a" #(assoc res :n-partitions (str->int %))
                "x" #(assoc res :x-cost (str->int %))
                "y" #(assoc res :y-cost (str->int %))
                "z" #(assoc res :z-cost (str->int %))
                "b" #(assoc res :bend-cost (str->int %))
                "p" (assoc res :print true)
                "m" (assoc res :profile true)
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
    (tufte/set-min-level! (if (:profile params) 0 6))
    (println "Variant         =" (:variant params))
    (let [maze
            (maze/read (:input-file params))
          paths-per-thread
            (ref [])
          [[results total-time] pstats]
            (profiled {:level 3 :dynamic? true}
              (p :all (time ; time/profile everything
                (parallel-for-all [_i (range (:n-threads params))]
                  (p :thread (time ; timer/profile per thread
                    (router/solve params maze paths-per-thread)))))))]
      ;(log "Paths (per thread):" @paths-per-thread)
      (println "Paths routed    =" (reduce + (map count @paths-per-thread)))
      (println "Elapsed time    =" total-time "milliseconds")
      (println "Time per thread:")
      (doseq [[_result thread-time] results]
        (println " " thread-time "milliseconds"))
      (print-tx-stats)
      ; verification of paths, also prints grid if asked to
      ; Note: (apply concat ...) flattens once, i.e. it turns the list of
      ; list of paths into a single list of paths (but each path is still a
      ; list of points)
      (if (maze/check-paths maze (apply concat @paths-per-thread)
            (:print params))
        (println "Verification passed.")
        (println "Verification FAILED!"))
      (when (:profile params) (println (format-pstats pstats)))
      (shutdown-agents))))

; To run manually:
;(main *command-line-args*)
