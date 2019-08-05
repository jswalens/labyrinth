(defproject labyrinth "2.0.0"
  :description "Labyrinth benchmark from STAMP in Clojure, using transactional futures."
  :url "http://soft.vub.ac.be/~jswalens/chocola/"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [com.taoensso/timbre "3.4.0"]]
  :resource-paths ["resources/chocola-2.0.0-standalone.jar"]
  :injections [(require 'chocola.core)]
  :main ^:skip-aot labyrinth.main
  :profiles {:uberjar {:aot :all}})
