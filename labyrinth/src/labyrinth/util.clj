(ns labyrinth.util)

(defn str->int [s]
  "Converts s to integer, returns nil in case of error"
  (try
    (Integer/parseInt s)
    (catch NumberFormatException e
      nil)))
