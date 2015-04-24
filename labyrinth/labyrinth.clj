(ns labyrinth
  (:require [maze]
            [router]))

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

(defn parse-args [args]
  "Parse the arguments.
  Will return strange results or throw exceptions on invalid input."
  ; Not very nice code, but should work.
  (reduce
    (fn [res arg]
      (if (.startsWith arg "-")
        ; Trick: for parameter names (e.g. -i), we return a function. In the
        ; next iteration, this will be filled in by calling this function with
        ; the parameter value.
        (case (.substring arg 1)
          "b" #(assoc res :bend-cost %)
          "t" #(assoc res :n-threads %)
          "x" #(assoc res :x-cost %)
          "y" #(assoc res :y-cost %)
          "z" #(assoc res :z-cost %)
          "i" #(assoc res :input-file %)
          "p" (assoc res :print true)
              (assoc res :arg-error true))
        ; Call previous result, assuming its a function that sets the right
        ; parameter.
        (res arg)))
    default-params args))

(defn main [args]
  "Main function. `args` should be a list of command line arguments."
  (let [params (parse-args args)]
    (if (or (:arg-error params) (nil? params))
      (do (println "Error parsing arguments")
          (println "Params:" params)
          (println usage))
      (let [maze             (maze/read (:input-file params))
            paths-per-thread (ref [])]
        (println maze)
        ; TODO: in new thread(s), and time this!
        (time (router/solve params maze paths-per-thread))
        ; Once everything is done
        (println "Paths routed    =" (count (apply concat @paths-per-thread)))
        ; Note: (apply concat ...) flattens once, i.e. it turns the list of
        ; list of paths into a single list of paths (but each path is still a
        ; list of points)
        (println "Paths (per thread):" @paths-per-thread)
        (println "Elapsed time    = XXX seconds")
        ; verification of paths, also prints grid if asked to
        (if (maze/check-paths maze (apply concat @paths-per-thread)
              (:print params))
          (println "Verification passed.")
          (println "Verification FAILED!"))))))

(main *command-line-args*)
