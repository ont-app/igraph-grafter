(ns ont-app.igraph-grafter.core
  (:require
  [grafter-2.rdf4j.repository :as repo
   :refer [
           ->connection
           sail-repo
           ]]
  [grafter.vocabularies.core :as gvoc
   :refer [->uri
           ]]
   [grafter-2.rdf.protocols :as grafter
    :refer [->Triple
            ->Quad
            add-batched
            ]]

   [ont-app.graph-log.core :as glog]
   [ont-app.graph-log.levels :refer :all]
   
   [ont-app.igraph-grafter.rdf :as rdf]
   [ont-app.igraph-grafter.ont :as ont]
   
  [ont-app.igraph.core :as igraph
    :refer
    [IGraph
     add-to-graph
     ask
     difference
     get-o
     get-p-o
     intersection
     invoke
     match-or-traverse
     mutability
     normal-form
     reduce-spo
     remove-from-graph
     subjects
     subtract
     traverse
     triples-format
     union
     unique
     ]]
  [ont-app.igraph.graph :as native-graph]
  [ont-app.igraph-grafter.rdf :as rdf]
  [ont-app.vocabulary.core :as voc]  
  ))


(voc/put-ns-meta!
 'ont-app.igraph-grafter.core
 {
  :voc/mapsTo 'ont-app.igraph-grafter.ont
  }
 )

(def ontology @ont/ontology-atom)

;; FUN WITH READER MACROS

#?(:cljs
   (enable-console-print!)
   )

#?(:cljs
   (defn on-js-reload [] )
   )

;; NO READER MACROS BEYOND THIS POINT


#_(defn make-graph
  ([]
   (sail-repo)))

^:reduce-kw-fn
(defn collect-kwis [macc k v]
  (assoc macc k
         (if (= (type v) java.net.URI)
           (voc/keyword-for (str v))
           v)))

(defn ask-query [conn query-string]
  (repo/query conn query-string))

(defn interpret-query [conn query-string]
  (map (fn [bmap]
         (reduce-kv collect-kwis {} bmap))
       (repo/query conn query-string)))

(defrecord GrafterGraph
    [conn graph-uri]
  IGraph
  (normal-form [this] (rdf/query-for-normal-form repo/query (:conn this)))
  (subjects [this] (rdf/query-for-subjects interpret-query (:conn this)))
  (get-p-o [this s] (rdf/query-for-p-o interpret-query (:conn this) s))
  (get-o [this s p] (rdf/query-for-o interpret-query (:conn this) s p))
  (ask [this s p o] (rdf/ask-s-p-o ask-query (:conn this) s p o))
  (igraph/query [this q] (interpret-query (:conn this) q))
  (mutability [this] ::igraph/mutable)
  #?(:clj clojure.lang.IFn
     :cljs cljs.core/IFn)
  (invoke [g] (normal-form g))
  (invoke [g s] (get-p-o g s))
  (invoke [g s p] (match-or-traverse g s p))
  (invoke [g s p o] (match-or-traverse g s p o))
  ;; igraph/IGraphMutable
  
  )

(defn make-graph [conn graph-uri]
  (->GrafterGraph conn graph-uri))

(defn render-element [elt]
  "Returns either a URI or a literal to be added to a graph"
  (if (keyword? elt)
    (->uri (voc/uri-for elt))
    (rdf/render-literal elt)))

^:reduce-fn
(defn collect-p-o 
  "Returns [<quad>, ...] with a quad added for `s`,  `p-o`, `graph-uri`
  Where
  <quad> is a Grafter Quad
  <s> is a URI for a subject
  <p-o> := [<p-kwi>, <o-elt>]
  <p-kwi> is the kwi for a predicate
  <o-elt> is a kwi or literal object
  <graph-uri> is a URI for some graph, or nil
  NOTE: typically called to compose material to add to a graph.
  "
  [graph-uri s acc p-o]
  {:pre [(vector? acc)]
   }
  (trace ::StartingCollectPO
        :log/graph-uri graph-uri
        :log/s s
        :log/acc acc
        :log/p-o p-o)
  (conj acc (apply ->Quad
                   (conj (reduce conj
                                 [s]
                                 (map render-element p-o))
                         graph-uri))))

(defmethod rdf/render-literal ::DatatypeURI
  [x]
  ;; grafter handles these automatically
  x)


(defmethod rdf/render-literal (type 0)
  [x]
  x)

(defn alter-graph
  "Side effect: Either adds or deletes the contents of `source-graph` from `g`
  Where
  <source graph> is a native-graph 'adapter' into which content to be removed
    has been read.
  <g> is a GrafterGraph
  <add-or-delete> := fn [conn [<Quad>, ...]]
    either grafter/add-batched or grafter/delete
  "
  [add-or-delete g source-graph]
  (let [collect-quad (fn [g-uri vacc s p o]
                       (collect-p-o g-uri
                                    (render-element s)
                                    vacc
                                    [p o]))
        ]
    (add-or-delete
     (:conn g)
     (igraph/reduce-spo (partial collect-quad
                                 (render-element (:graph-uri g)))
                        []
                        source-graph))))


(defmethod igraph/add-to-graph [GrafterGraph :vector]
  [g to-add]
  {:pre [(= (igraph/mutability g) ::igraph/mutable)]
   }
  (alter-graph add-batched g (add (native-graph/make-graph)
                                to-add))
  
  #_(let [
        graph-uri (render-element (:graph-uri g))
        s-uri (render-element (first to-add))
        ]
    (grafter/add
     (:conn g)
     (value-trace
      ::CollectedPOsFromVector
      (reduce (partial collect-p-o graph-uri s-uri)
              []
              (partition 2 (rest to-add)))))
         
    g))

#_(defn alter-graph-per-vectors
  [add-or-delete g to-alter]
  (let [
        _collect-p-o (partial collect-p-o (render-element (:graph-uri g)))
        collect-vector (fn [vacc v]
                         (reduce (partial _collect-p-o
                                          (render-element (first v)))       
                                 vacc
                                 (partition 2 (rest v))))
        ]
    (add-or-delete
     (:conn g)
     (value-trace
      ::CollectedVectorOfVectors
      (reduce collect-vector [] to-alter)))))

  
(defmethod igraph/add-to-graph [GrafterGraph :vector-of-vectors]
  [g to-add]
  {:pre [(= (igraph/mutability g) ::igraph/mutable)]
   }
  (alter-graph add-batched g (add (native-graph/make-graph)
                                  to-add))
  #_(let [
        _collect-p-o (partial collect-p-o (render-element (:graph-uri g)))
        collect-vector (fn [vacc v]
                         (reduce (partial _collect-p-o
                                          (render-element (first v)))       
                                 vacc
                                 (partition 2 (rest v))))
        ]
    (add-batched (:conn g)
                 (value-trace
                  ::CollectedVectorOfVectors
                  (reduce collect-vector [] to-alter))))
    
  g)


  
(defmethod igraph/add-to-graph [GrafterGraph :normal-form]
  [g to-add]
  (alter-graph add-batched g (add (native-graph/make-graph)
                                  to-add))

  #_(let [collect-quad (fn [g-uri vacc s p o]
                       (collect-p-o g-uri
                                    (render-element s)
                                    vacc
                                    [p o]))
        ]
    (add-batched
     (:conn g)
     (igraph/reduce-spo (partial collect-quad
                                 (render-element (:graph-uri g)))
                        []
                        (native-graph/make-graph :contents to-add)))
    )

  g)



(defn dummy-fn []
  "The eagle has landed")
    
