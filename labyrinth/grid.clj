(ns grid)

(defn alloc []
  "Returns a 'default' grid.
  In the C++ version, this does allocations; in Clojure we don't actually really
  need this. The C++ version also ensures the points are aligned in the cache,
  we don't do this."
  {:width  nil
   :height nil
   :depth  nil
   :points []})
