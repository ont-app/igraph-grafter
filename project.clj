(defproject ont-app/igraph-grafter "0.1.0-SNAPSHOT"
  :description "FIXME"
  :url "https://github.com/ont-app/igraph-grafter"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [;; for deps :tree
                 
                 [org.clojure/clojure "1.10.1"]
                 [org.clojure/clojurescript "1.10.597"]
                 [org.clojure/spec.alpha "0.2.187"]
                 ;; 3rd party libs
                 [cheshire "5.10.0"]
                 [com.cognitect/transit-clj "1.0.324"]
                 [com.fzakaria/slf4j-timbre "0.3.19"]
                 [com.taoensso/timbre "4.11.0-alpha1"]
                 [grafter "2.1.8"]
                 [lein-doo "0.1.11"]
                 [selmer "1.12.24"]
                 ;; Ont-app libs
                 [ont-app/graph-log "0.1.1-SNAPSHOT"]
                 [ont-app/igraph "0.1.5-SNAPSHOT"]
                 [ont-app/igraph-vocabulary "0.1.1-SNAPSHOT"]
                 [ont-app/rdf "0.1.0-SNAPSHOT"]
                 [ont-app/vocabulary "0.1.1-SNAPSHOT"]
                 ]
  
  ;; :main ^:skip-aot ont-app.igraph-grafter.core
  :target-path "target/%s"
  :resource-paths ["resources" "target/cljsbuild"]
  
  :plugins [[lein-codox "0.10.6"]
            [lein-cljsbuild "1.1.7"
             :exclusions [[org.clojure/clojure]]]
            [lein-doo "0.1.11"]
            ]
  :source-paths ["src"]
  :test-paths ["src" "test"]
  :cljsbuild
  {
   :test-commands {"test" ["lein" "doo" "node" "test" "once"]}
   :builds
   {
    :dev {:source-paths ["src"]
           :compiler {
                      :main ont-app.igraph-grafter.core 
                      :asset-path "js/compiled/out"
                      :output-to "resources/public/js/igraph-grafter.js"
                      :source-map-timestamp true
                      :output-dir "resources/public/js/compiled/out"
                      :optimizations :none
                      }
          }
    ;; for testing the cljs incarnation
    ;; run with 'lein doo node test
    :test {:source-paths ["src" "test"]
           :compiler {
                      :main ont-app.igraph-grafter.doo
                      :target :nodejs
                      :asset-path "resources/test/js/compiled/out"
                      :output-to "resources/test/compiled.js"
                      :output-dir "resources/test/js/compiled/out"
                      :optimizations :advanced ;;:none
                      }
           }
   }} ;; end cljsbuild

  :codox {:output-path "doc"}

  :profiles {:uberjar {:aot :all}
             :dev {:dependencies [[binaryage/devtools "1.0.0"]
                                  ]
                   :source-paths ["src"] 
                   :clean-targets
                   ^{:protect false}
                   ["resources/public/js/compiled"
                    "resources/test"
                    :target-path
                    ]
                   }
             })
