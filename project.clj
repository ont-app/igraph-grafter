(defproject ont-app/igraph-grafter "0.1.1-SNAPSHOT"
  :description "Wrapper around swirrl/grafter for IGraph protocols."
  :url "https://github.com/ont-app/igraph-grafter"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [;; disambiguate deps :tree
                 [com.google.guava/guava "25.1-jre"]
                 [commons-codec "1.14"] ;; [commons-codec "1.11"]
                 ;; clojure core
                 [org.clojure/clojure "1.10.1"]
                 [org.clojure/spec.alpha "0.2.187"]
                 ;; 3rd party libs
                 [cheshire "5.10.0"]
                 [com.cognitect/transit-clj "1.0.324"]
                 [com.fzakaria/slf4j-timbre "0.3.19"]
                 [com.taoensso/timbre "4.11.0-alpha1"]
                 [grafter "2.1.8"]
                 ;; Ont-app libs
                 [ont-app/graph-log "0.1.1"]
                 [ont-app/rdf "0.1.0"]
                 ]
  
  :target-path "target/%s"
  :source-paths ["src"]
  :test-paths ["src" "test"]

  :plugins [[lein-codox "0.10.6"]
            ]
  :codox {:output-path "doc"}

  :profiles {
             })
