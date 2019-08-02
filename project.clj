(defproject labyrinth "2.0.0"
  :description "Labyrinth benchmark from STAMP in Clojure, using transactional futures."
  :url "http://soft.vub.ac.be/~jswalens/chocola/"
  :dependencies [[com.taoensso/timbre "3.4.0"]]
  :resource-paths ["resources/clojure-1.6.0-transactional-futures-2.3.jar"]
  :main ^:skip-aot labyrinth.main
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
