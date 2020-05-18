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
   [grafter.url :as gurl
    :refer [->url
            ]]
   [grafter.vocabularies.core :as gvoc
    :refer [->uri
            ]]
   [grafter-2.rdf.protocols :as grafter
    :refer [->Triple
            ->Quad
            add
            add-batched
            ]]
   ;; [ont-app.sparql-client.core :as sparql-client]
   [ont-app.igraph.core :as igraph
    :refer [add!
            subtract!
            ]]
   [ont-app.igraph.graph :as native-graph]
   [ont-app.graph-log.core :as glog]
   [ont-app.graph-log.levels :refer :all]
   [ont-app.igraph-grafter.core :as igraphter]
   [ont-app.rdf.core :as rdf]
   [ont-app.vocabulary.core :as voc]
   )
  (:import
   [org.eclipse.rdf4j.model.impl SimpleValueFactory
    org.eclipse.rdf4j.repository.sail SailRepositoryConnection
    ]
   ))

(glog/log-reset!)
;; (glog/set-level! ::StartingCollectPO :glog/WARN)
;; (glog/set-level! ::CollectedPOsFromVector :glog/WARN)
;; (glog/set-level! ::CollectedVectorOfVectors :glog/WARN)
;; (glog/set-level! ::rdf/CollectNormalFormBinding :glog/WARN)

(def the igraph/unique)

(def repo (sail-repo))
(def conn (->connection repo))


(def g (igraphter/make-graph conn ::Test))

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
  (glog/set-level! ::rdf/QueryForNormalForm :glog/INFO)
  (testing "New graph with new name should be empty"
    (let [g' (igraphter/make-graph conn ::Test2)]
      (is (= (igraph/normal-form g')
             {})))))

;; (add conn T)

;; (def response (query conn "SELECT * WHERE {?s ?p ?o}"))


#_(defn subjects [conn]
  (map (fn [bmap] (-> bmap
                      (:s)
                      (str)
                      (voc/keyword-for)))
       (query conn "SELECT * WHERE {?s ?p ?o}")))

;; (defn collect-kwi [macc k v]
;;   (assoc macc k (voc/keyword-for v)))
  
;; (defn query-for-spo [conn]
;;   (map (fn [bmap]
;;          (reduce-kv collect-kwi {} bmap))
;;        (query conn "SELECT * WHERE {?s ?p ?o}")))

;; (defn interpret-query [conn query-string]
;;   (map (fn [bmap]
;;          (reduce-kv collect-kwi {} bmap))
;;        (query conn query-string)))



;; (def subjects (rdf/query-for-subjects interpret-query conn))

;; (def normal-form (rdf/query-for-normal-form interpret-query conn))

;; (def po (rdf/query-for-p-o interpret-query conn ::A))

;; (def o (rdf/query-for-o interpret-query conn ::A ::A))

;; (def answer (rdf/ask-s-p-o ask-query conn ::A ::A ::A))

;; (def to-add [::A ::A ::B])



