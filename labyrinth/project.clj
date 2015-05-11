(defproject labyrinth "0.1.0-SNAPSHOT"
  :description "STAMP Labyrinth in Clojure"
  :url "http://example.com/TODO"
  :dependencies [[org.clojure/clojure "1.6.0"]]
  :main ^:skip-aot labyrinth.main
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
