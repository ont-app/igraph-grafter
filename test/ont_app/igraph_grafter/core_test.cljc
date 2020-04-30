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
   [ont-app.igraph.core :as igraph]
   [ont-app.igraph.graph :as native-graph]
   [ont-app.graph-log.core :as glog]
   [ont-app.graph-log.levels :refer :all]
   [ont-app.igraph-grafter.core :as igraphter]
   [ont-app.igraph-grafter.rdf :as rdf]
   [ont-app.vocabulary.core :as voc]
   )
  (:import
   [org.eclipse.rdf4j.model.impl SimpleValueFactory]
   ))

(glog/log-reset!)
(glog/set-level! ::StartingCollectPO :glog/WARN)
(glog/set-level! ::CollectedPOsFromVector :glog/WARN)
(glog/set-level! ::CollectedVectorOfVectors :glog/WARN)

(def the igraph/unique)

;; (def TheValueFactory (. SimpleValueFactory getInstance))

;; (def A (.createIRI TheValueFactory "http://exammple.com/A"))

;; (def AAA (.createStatement TheValueFactory
;;                            A
;;                            A
;;                            A
;;                            A))


;; (def A (->url "http://example.com/A"))
        ;; (def A (->url (voc/uri-for ::A)))
        ;; (def T (->Triple A A A))

(def repo (sail-repo))
(def conn (->connection repo))

(def g (igraphter/make-graph conn ::Test1))
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



(deftest dummy-test
  (testing "fixme"
    (is (= 1 2))))
