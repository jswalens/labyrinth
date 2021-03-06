(ns labyrinth.util
  (:refer-clojure :exclude [time]))

(defn str->int [s]
  "Converts s to integer, returns nil in case of error"
  (try
    (Integer/parseInt s)
    (catch NumberFormatException e
      nil)))

(defmacro time [expr]
  "Based on Clojure's time, but returns [result time],
  instead of printing to *out*."
  `(let [start# (. System (nanoTime))
         ret#   ~expr
         time#  (/ (double (- (. System (nanoTime)) start#)) 1000000.0)]
     [ret# time#]))

(def n-tx (atom 0))
(def tries-per-tx (atom {}))
(def time-per-tx (atom {}))

(defmacro dosync-tracked [& body]
  `(do
    (let [tx-i# (swap! n-tx inc)
          [result# time#]
            (time
              (dosync
                (swap! tries-per-tx #(assoc % tx-i# (inc (get % tx-i# 0))))
                ~@body))]
      (swap! time-per-tx #(assoc % tx-i# time#))
      result#)))

(defn- avg [xs]
  (double (/ (reduce + xs) (count xs))))

(defn print-tx-stats []
  (println "Number of tracked transactions:" @n-tx)
  (println "Tries per transaction:" @tries-per-tx)
  (println "Average tries per transaction:" (avg (vals @tries-per-tx)))
  (println "Time per transaction:" @time-per-tx))
