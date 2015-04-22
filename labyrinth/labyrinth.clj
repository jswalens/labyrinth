(ns labyrinth
  (:require [maze]))

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
  "Parse the arguments. Not very nice, but should work."
  (reduce
    (fn [res arg]
      (if (.startsWith arg "-")
        (case (.substring arg 1)
          "b" #(assoc res :bend-cost %)
          "t" #(assoc res :n-threads %)
          "x" #(assoc res :x-cost %)
          "y" #(assoc res :y-cost %)
          "z" #(assoc res :z-cost %)
          "i" #(assoc res :input-file %)
          "p" (assoc res :print true)
              (assoc res :arg-error true))
        (res arg)))
    default-params args))

(defn main [args]
  "Main function. `args` should be a list of command line arguments."
  (let [params (parse-args args)]
    (if (or (:arg-error params) (nil? params))
      (do (println "Error parsing arguments")
          (println params)
          (println usage))
      (let [maze (maze/read (maze/alloc) (:input-file params))]
        (println maze)))))

(main *command-line-args*)
