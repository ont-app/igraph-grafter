(defproject ont-app/igraph-grafter "0.1.1-SNAPSHOT"
  :description "Wrapper around swirrl/grafter for IGraph protocols."
  :url "https://github.com/ont-app/igraph-grafter"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [;; Per 'lein nvd check':
                 [commons-beanutils "1.9.4"]
                 [org.apache.commons/commons-compress "1.21"]
                 [commons-io "2.11.0"]
                 ;; NOTE: Issues CVE-2018-10237, CVE-2020-8908 (medium risk) still not addressed in latest guava (23.0)
                 ;; NOTE: Issue CVE-2020-13956 (medium risk) still not addressed in latest httpclient (4.5.13)
                 ;; disambiguate deps :tree
                 [org.clojure/spec.alpha "0.3.218"]
                 [cheshire "5.10.1"]
                 ;; clojure core
                 [org.clojure/clojure "1.10.3"]
                 ;; 3rd party libs
                 [grafter "2.1.16"]
                 ;; Ont-app libs
                 [ont-app/graph-log "0.1.5"
                  :exclusions [org.clojure/clojurescript]
                  ]
                 [ont-app/rdf "0.1.4"]
                 ]
  
  :target-path "target/%s"
  :source-paths ["src"]
  :test-paths ["src" "test"]

  :plugins [[lein-codox "0.10.6"]
            ]
  :codox {:output-path "doc"}

  :profiles {
             })
