(ns ont-app.igraph-grafter.core
  (:require
   [clojure.spec.alpha :as spec]
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
   [ont-app.igraph-vocabulary.core :as igv]
   [ont-app.rdf.core :as rdf]
   [ont-app.vocabulary.core :as voc]  
   ))


(voc/put-ns-meta!
 'ont-app.igraph-grafter.core
 {
  :voc/mapsTo 'ont-app.igraph-grafter.ont
  :doc "Defines `GrafterGraph` record, which implements the `IGraph`,
  `IGraphMutable` and `IFn` protocols."
  :author "Eric D. Scott"
  } )

(def ontology (reduce igraph/union
                      [igv/ontology
                       rdf/ontology
                       @ont/ontology-atom]))

;; FUN WITH READER MACROS

#?(:cljs
   (enable-console-print!)
   )

#?(:cljs
   (defn on-js-reload [] )
   )

;; NO READER MACROS BEYOND THIS POINT (except in def of GrafterGraph)


(def date-time-regex
  "Matches the date-time-str encoded for #inst's, e.g. '2000-01-01T00:00:00Z'"
  #"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$")

(spec/def ::date-time-str
  (fn [s] (and (string? s) (re-matches date-time-regex s))))

^:reduce-kv-fn
(defn collect-kwis-and-literals [macc k v]
  "Returns `macc`' substituing translation of `v` -> idomatic igraph datatypes
Where
<macc> := {<k> <v'>, ...}, translated from a binding map from a query
<k> is a var in a query
<v> is the raw value bound to <k>
<v'> is the translation of <v> into idiomatic igraph datatypes:
  URIs -> KWIs
  {:lang  ... :string ...} #lstr <string>@<lang>
  .*^^transit:json -> decoded transit
"
  (trace ::StartingKwisAndLiterals
         :log/k k
         :log/v v)
    (assoc macc k
           (cond
             ;; URIs ...
             (= (type v) java.net.URI)
             (voc/keyword-for (str v))
             ;; Instant
             (spec/valid? ::date-time-str v)
             (clojure.instant/read-instant-date v)
             ;; Transit-tagged data ...
             (spec/valid? ::rdf/transit-tag v)
             (let [matches (re-matches rdf/transit-re v)]
               (rdf/read-transit-json (matches 1)))
             ;; language-tagged strings ...
             (and (map? v) (:string v) (:lang v))
             (rdf/->LangStr (:string v) (name (:lang v)))
             ;; otherwise just return v
             :default v)))

(defn ask-query [conn query-string]
  "Returns true/false for `query-string` posed to `conn`"
  (repo/query conn query-string))

(defn interpret-query [conn query-string]
  "Returns (<bmap'>, ...)  returned from `query-string` posed to `conn`
Where
<query-string> is a SPARQL query
<conn> is a grafter connection implementing the proper interfaces for a query
<bmap'> := <bmap>, with datatypes translated to idiomatic igraph clojure objects 
  as appropriate, per the `collect-kwis-and-literals` function.
<bmap> is a binding map returned by posing <query-string> to <conn>
"
  (map (fn [bmap]
         (reduce-kv collect-kwis-and-literals {} bmap))
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

(defn make-graph 
  "Returns an instance of `GrafterGraph` for `conn` and `graph-kwi`
  Where
  <conn> implements the grafter ITripleReadable, ISPARQLable,
    ITripleWriteable, ITripleDeleteable, and ITransactable protocols
  <graph-kwi> is a keyword mapped to a URI by voc namespace metadata.
See also the documentation for ont-app.vocabulary.core
"
  [conn graph-kwi]
  {:pre [(satisfies? grafter/ITripleReadable conn)
         (satisfies? grafter/ISPARQLable conn)
         (satisfies? grafter/ITripleWriteable conn)
         (satisfies? grafter/ITripleDeleteable conn)
         (satisfies? grafter/ITransactable conn)
         ]
   }
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
  "Returns :grafter/Instant,  :grafter/DatatypeURI or nil for `x`.
Where
<x> is a literal value 
`:grafter/Instant` triggers handling of an #inst
`:grafter/DatatypeURI` triggers handling as an xsd value or other ^^datatype.
`nil` signals handling with standard logic for literals per
  `rdf/render-literal-dispatch`
"
  
  (cond (inst? x)
        :grafter/Instant
        
        (satisfies? grafter/IDatatypeURI x)
        :grafter/DatatypeURI))

;; This will inform rdf/render-literal-dispatch of platform-specific dispatches:
(reset! rdf/special-literal-dispatch special-literal-dispatch)


(defmethod rdf/render-literal :grafter/Instant
  [ts]
  (str (.toInstant ts))) ;;"^^" grafter.vocabularies.xsd/xsd:dateTime))

(defmethod rdf/render-literal :grafter/DatatypeURI
  [x]
  ;; grafter handles these automatically
  x)

;; Grafter has its own native LangStr regime...
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
  (warn ::StartingAlterGraph
         :log/source-graph source-graph)
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
                 (if (and s (not p)) ;; [<s>]
                   (add adapter
                        ^{::igraph/triples-format :normal-form}
                        {s (g s)})
                   ;; else [<s> <p>]
                   (add adapter
                        ^{::igraph/triples-format :normal-form}
                        {s {p (g s p)}}))))
  g)

