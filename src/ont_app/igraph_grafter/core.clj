(ns ont-app.igraph-grafter.core
   {
    :voc/mapsTo 'ont-app.igraph-grafter.ont
    :author "Eric D. Scott"
    :doc "Defines `GrafterGraph` record, which implements the `IGraph`, 
`IGraphMutable` and `IFn` protocols."
    } 
  (:require
   [clojure.spec.alpha :as spec]
   [grafter-2.rdf4j.repository :as repo]
   [grafter.vocabularies.core :as gvoc
    :refer [->uri
            ]]
   [grafter-2.rdf.protocols :as grafter
    :refer [->Quad
            add-batched
            delete
            ]]
   [ont-app.graph-log.levels
    :refer [trace
            warn
            ]]
   [ont-app.igraph.core :as igraph
    :refer
    [IGraph
     add
     add!
     add-to-graph
     ask
     get-o
     get-p-o
     match-or-traverse
     mutability
     normal-form
     reduce-spo
     remove-from-graph
     subjects
     subtract!
     union
     unique
     ]]
   [ont-app.igraph.graph :as simple-graph]
   [ont-app.igraph-vocabulary.core :as igv]
   [ont-app.rdf.core :as rdf-app]
   [ont-app.vocabulary.core :as voc]
   [ont-app.vocabulary.lstr :refer [->LangStr lang]]
   )
  (:import
   [org.eclipse.rdf4j.repository.sail SailRepositoryConnection]
   )
  )

(def ontology (reduce union
                      [igv/ontology
                       rdf-app/ontology]))


(def date-time-regex
  "Matches the date-time-str encoded for #inst's, e.g. '2000-01-01T00:00:00Z'"
  #"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$")

(spec/def ::date-time-str
  (fn [s] (and (string? s) (re-matches date-time-regex s))))

;; a reduce-kv-fn
^:Map
(defn collect-kwis-and-literals [macc k v]
  "Returns `macc`' substituing  `v` -> <v'>
Where
<macc> := {<k> <v'>, ...}, translated from a binding map from a query
<k> is a var in a query, :~ ':?.*'
<v> is the raw value bound to <k> by the query response
<v'> is the translation of <v> into idiomatic igraph datatypes:
  URIs -> KWIs
  <data-time string> -> #inst ....
  {:lang  ... :string ...} #lstr <string>@<lang>
  .*^^transit:json -> decoded transit
  Otherwise, just <v>
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
             (spec/valid? ::rdf-app/transit-tag v)
             (let [matches (re-matches rdf-app/transit-re v)]
               (rdf-app/read-transit-json (matches 1)))
             ;; language-tagged strings ...
             (and (map? v) (:string v) (:lang v))
             (->LangStr (:string v) (name (:lang v)))
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
    ;; IGraph wrapper for <conn> with named graph <graph-kiwi>
    [conn graph-kwi]
  IGraph
  (normal-form [this] (rdf-app/query-for-normal-form graph-kwi
                                                 interpret-query
                                                 (:conn this)))
  (subjects [this] (rdf-app/query-for-subjects  graph-kwi
                                            interpret-query
                                            (:conn this)))
  (get-p-o [this s] (rdf-app/query-for-p-o graph-kwi
                                       interpret-query
                                       (:conn this) s))
  (get-o [this s p] (rdf-app/query-for-o graph-kwi
                                     interpret-query
                                     (:conn this) s p))
  (ask [this s p o] (rdf-app/ask-s-p-o graph-kwi
                                   ask-query
                                   (:conn this) s p o))
  (igraph/query [this q] (interpret-query (:conn this) q))
  (mutability [this] ::igraph/mutable)

  clojure.lang.IFn
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
  <conn> is a SailRepositoryconnection
  <graph-kwi> is a keyword mapped to a URI by voc namespace metadata.
See also the documentation for ont-app.vocabulary.core
"
  [conn graph-kwi]
  {:pre [(instance? SailRepositoryConnection conn)  
         ]
   }
  (->GrafterGraph conn graph-kwi))

(defn render-element [elt]
  "Returns either a URI or a literal to be added to a graph"
  (if (keyword? elt)
    (->uri (voc/uri-for elt))
    (rdf-app/render-literal elt)))

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
  "Returns :rdf-app/Instant,  :rdf-app/DatatypeURI or nil for `x`.
Where
<x> is a literal value 
`:rdf-app/Instant` triggers handling of an #inst
`:rdf-app/DatatypeURI` triggers handling as an xsd value or other ^^datatype.
`nil` signals handling with standard logic for literals per
  `rdf-app/render-literal-dispatch`
"
  
  (cond (inst? x)
        :rdf-app/Instant
        
        (satisfies? grafter/IDatatypeURI x)
        :rdf-app/XsdDatatype))

;; This will inform rdf-app/render-literal-dispatch of platform-specific
;; dispatches:
(reset! rdf-app/special-literal-dispatch special-literal-dispatch)


(defmethod rdf-app/render-literal :rdf-app/Instant
  [ts]
  (str (.toInstant ts))) ;;"^^" grafter.vocabularies.xsd/xsd:dateTime))

(defmethod rdf-app/render-literal :rdf-app/XsdDatatype
  [x]
  ;; grafter handles these automatically
  x)

;; Grafter has its own native LangStr regime...
(defmethod rdf-app/render-literal :rdf-app/LangStr
  [lstr]
  (grafter-2.rdf.protocols/->LangString
   (str lstr)
   (lang lstr)))

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
  (trace ::StartingAlterGraph
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
