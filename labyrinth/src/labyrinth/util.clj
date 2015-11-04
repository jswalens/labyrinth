(ns labyrinth.util)

(defn str->int [s]
  "Converts s to integer, returns nil in case of error"
  (try
    (Integer/parseInt s)
    (catch NumberFormatException e
      nil)))

(defmacro time [expr]
  "Based on Clojure's time, but returns {:time time :result value},
  instead of printing to *out*."
  `(let [start# (. System (nanoTime))
         ret#   ~expr
         time#  (/ (double (- (. System (nanoTime)) start#)) 1000000.0)]
     {:time time# :result ret#}))

(def n-tx (atom 0))
(def tries-per-tx (atom {}))
(def time-per-tx (atom {}))

(defmacro dosync-tracked [& body]
  `(do
    (let [tx-i# (swap! n-tx inc)
          {time# :time result# :result}
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
