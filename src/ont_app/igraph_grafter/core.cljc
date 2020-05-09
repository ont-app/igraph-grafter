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
            delete
            ]]

   [ont-app.graph-log.core :as glog]
   [ont-app.graph-log.levels :refer :all]
   
   [ont-app.igraph-grafter.rdf :as rdf]
   [ont-app.igraph-grafter.ont :as ont]
   
   [ont-app.igraph.core :as igraph
    :refer
    [IGraph
     add
     add!
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
     subtract!
     traverse
     triples-format
     union
     unique
     ]]
   [ont-app.igraph.graph :as simple-graph]
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

(defn collect-kwis-and-lstrs [macc k v]
  (warn ::starting-kwis-and-lstrs
         :log/k k
         :log/v v)
  (assoc macc k
         (cond
           (= (type v) java.net.URI) (voc/keyword-for (str v))
           (and (map? v) (:string v) (:lang v))
           (rdf/->LangStr (:string v) (name (:lang v)))
           :default v)))

(defn ask-query [conn query-string]
  (repo/query conn query-string))

(defn interpret-query [conn query-string]
  (map (fn [bmap]
         (reduce-kv collect-kwis-and-lstrs {} bmap))
       (repo/query conn query-string)))

(defrecord GrafterGraph
    [conn graph-kwi]
  IGraph
  (normal-form [this] (rdf/query-for-normal-form interpret-query (:conn this)))
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

  igraph/IGraphMutable
  (add! [g to-add] (add-to-graph g to-add))
  (subtract! [g to-remove] (remove-from-graph g to-remove))
  )

(defn make-graph [conn graph-kwi]
  (->GrafterGraph conn graph-kwi))

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
  {:pre [(vector? acc)
         (uri? graph-uri)
         ]
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

(defn special-literal-dispatch [x]
  "Returns ::DatatypeURI dispatch value as approprite for `x`.
NOTE: this is used to field stuff like XSD values."
  (cond (inst? x)
        :grafter/Instant
        (satisfies? grafter/IDatatypeURI x)
        :grafter/DatatypeURI))

(reset! rdf/special-literal-dispatch special-literal-dispatch)

(defmethod rdf/render-literal :grafter/Instant
  [ts]
  (str (.toInstant ts))) ;;"^^" grafter.vocabularies.xsd/xsd:dateTime))

(defmethod rdf/render-literal :grafter/DatatypeURI
  [x]
  ;; grafter handles these automatically
  x)

(defmethod rdf/render-literal ::rdf/LangStr
  [lstr]
  (grafter-2.rdf.protocols/->LangString
   (str lstr)
   (rdf/lang lstr)))

(defn alter-graph
  "Side effect: Either adds or deletes the contents of `source-graph` from `g`
  Where
  <source graph> is a simple-graph 'adapter' into which content to be removed
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
                                 (render-element (:graph-kwi g)))
                        []
                        source-graph))))

(defmethod add-to-graph [GrafterGraph :vector]
  [g to-add]
  {:pre [(= (igraph/mutability g) ::igraph/mutable)]
   }
  (alter-graph add-batched g (add (simple-graph/make-graph)
                                  ^{::igraph/triples-format :vector}
                                  to-add)))

(defmethod add-to-graph [GrafterGraph :vector-of-vectors]
  [g to-add]
  {:pre [(= (igraph/mutability g) ::igraph/mutable)]
   }
  (alter-graph add-batched g (add (simple-graph/make-graph)
                                  ^{::igraph/triples-format :vector-of-vectors}
                                  to-add))
    
  g)

(defmethod add-to-graph [GrafterGraph :normal-form]
  [g to-add]
  (alter-graph add-batched g (add (simple-graph/make-graph)
                                  ^{::igraph/triples-format :normal-form}
                                  to-add))


  g)

(defmethod igraph/remove-from-graph [GrafterGraph :vector]
  [g to-remove]
  (alter-graph delete g (add (simple-graph/make-graph)
                             ^{::igraph/triples-format :vector}
                             to-remove))
  g)

(defmethod igraph/remove-from-graph [GrafterGraph :vector-of-vectors]
  [g to-remove]
  (alter-graph delete g (add (simple-graph/make-graph)
                             ^{::igraph/triples-format :vector-of-vectors}
                             to-remove))
  g)

(defmethod igraph/remove-from-graph [GrafterGraph :normal-form]
  [g to-remove]
  (alter-graph delete g (add (simple-graph/make-graph)
                             ^{::igraph/triples-format :normal-form}
                             to-remove))
  g)

(defmethod igraph/remove-from-graph [GrafterGraph :underspecified-triple]
  [g to-remove]
  (let [adapter (simple-graph/make-graph)
        s (first to-remove)
        p (unique (rest to-remove))
        ]
    (alter-graph delete g
                 (if (and s (not p))
                   (add adapter
                        ^{::igraph/triples-format :normal-form}
                        {s (g s)})
                   ;; else there's p
                   (add adapter
                        ^{::igraph/triples-format :normal-form}
                        {s {p (g s p)}}))))
  g)

