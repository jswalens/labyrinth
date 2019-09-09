(defproject labyrinth "2.0.0"
  :description "Labyrinth benchmark from STAMP in Clojure, using transactional futures."
  :url "http://soft.vub.ac.be/~jswalens/chocola/"
  :dependencies [[com.taoensso/tufte "2.1.0" :exclusions [org.clojure/clojure]]]
  :resource-paths ["resources/chocola-2.0.0-standalone.jar"]
  :injections [(require 'chocola.core)]
  :main ^:skip-aot labyrinth.main
  :profiles {:uberjar {:aot :all}})
