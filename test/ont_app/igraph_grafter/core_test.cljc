(ns ont-app.igraph-grafter.core-test
  {
   :vann/preferredNamespacePrefix "test"
   :vann/preferredNamespaceUri "http://naturallexicon.org/grafter/core-test#"
   }
  (:require
   #?(:cljs [cljs.test :refer-macros [async deftest is testing]]
      :clj [clojure.test :refer :all])
   [grafter-2.rdf4j.repository :as repo
    :refer [
            ->connection
            query
            sail-repo
            ]]
   [ont-app.igraph.core :as igraph
    :refer [add!
            subtract!
            normal-form
            union
            ]]
   [ont-app.igraph.graph :as simple-graph]
   [ont-app.igraph.core-test :as igraph-test]
   [ont-app.graph-log.core :as glog]
   [ont-app.graph-log.levels :refer :all]
   [ont-app.igraph-grafter.core :as igraphter
    :refer [make-graph
            ]]
   [ont-app.rdf.core :as rdf]
   [ont-app.vocabulary.core :as voc]
   ))

(glog/log-reset!)

(def the igraph/unique)

;; Acquire an in-memory repo, and a connection...
(def repo (sail-repo))
(def conn (->connection repo))

(def g (make-graph conn ::Test))

(deftest literals-tests
  (glog/log-reset!)
  (glog/set-level! ::igraphter/StartingKwisAndLiterals :glog/INFO)
  (testing "literals should render and complete round trip"
    (let [i #inst "2000"
          ]
      (add! g [::LiteralsTest ::hasInst i])
      (is (= (rdf/render-literal i)
             "2000-01-01T00:00:00Z"))
      (is (= (the (g ::LiteralsTest ::hasInst))
             i)))
    (let [i 1]
      (add! g [::LiteralsTest ::hasInt i])
      (is (= (rdf/render-literal 1)
             1))
      (is (= (the (g ::LiteralsTest ::hasInt))
             1)))
    (let [ls #lstr "blah@en"
          lsl (rdf/render-literal #lstr "blah@en")]
      (add! g [::LiteralsTest ::hasLangStr ls])
      (is (instance? grafter_2.rdf.protocols.LangString lsl))
      (is (= (:string lsl "blah")))
      (is (= (:lang lsl "en")))
      (is (= (the (g ::LiteralsTest ::hasLangStr))
             ls))
      )
    ))

(deftest new-name-new-graph
  (glog/log-reset!)
  (testing "New graph with new name should be empty"
    (let [g' (make-graph conn ::Test2)]
      (is (= (igraph/normal-form g')
             {})))))

(deftest test-readme
  (glog/log-reset!)
  (testing "igraph readme stuff"
    (reset! igraph-test/eg
            (make-graph conn ::igraph-test/graph_eg))
    (reset! igraph-test/other-eg
            (make-graph conn ::igraph-test/graph_other-eg))
    (reset! igraph-test/eg-with-types
            (make-graph conn ::igraph-test/graph_eg-with-types))
    (reset! igraph-test/eg-for-cardinality-1
            (make-graph conn ::igraph-test/eg-for-cardinality-1))
    (reset! igraph-test/mutable-eg
            (make-graph  conn ::igraph-test/mutable-eg))
        
    (add! @igraph-test/eg igraph-test/eg-data)
    (add! @igraph-test/other-eg igraph-test/other-eg-data)

    (add! @igraph-test/eg-with-types
          (normal-form
           (union (simple-graph/make-graph
                   :contents igraph-test/eg-data)
                  (simple-graph/make-graph
                   :contents igraph-test/types-data))))
    (add! @igraph-test/eg-for-cardinality-1
          (normal-form
           (reduce union
                   (simple-graph/make-graph
                    :contents igraph-test/eg-data)
                   [
                    (simple-graph/make-graph
                     :contents igraph-test/types-data)
                    (simple-graph/make-graph
                     :contents igraph-test/cardinality-1-appendix)
                    ])))

    (igraph-test/readme)

    ;; Mutable model...
    (add! @igraph-test/mutable-eg igraph-test/eg-data)
    (igraph-test/readme-mutable)
    ))


