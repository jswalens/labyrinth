(defproject labyrinth "0.1.0-SNAPSHOT"
  :description "STAMP Labyrinth in Clojure"
  :url "http://example.com/TODO"
  :dependencies [[com.taoensso/timbre "3.4.0"]]
  :resource-paths ["resources/clojure-1.6.0.jar"]
  :main ^:skip-aot labyrinth.main
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
