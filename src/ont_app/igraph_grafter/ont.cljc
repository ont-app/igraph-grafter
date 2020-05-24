(ns ont-app.igraph-grafter.ont
  (:require
   ;;
   [ont-app.igraph.core :as igraph :refer [add]]
   [ont-app.igraph.graph :as simple-graph :refer [make-graph]]
   [ont-app.igraph-vocabulary.core :as igv]
   [ont-app.vocabulary.core :as voc]
   )
  )

(voc/put-ns-meta!
 'ont-app.igraph-grafter.ont
 {:vann/preferredNamespacePrefix "grafter"
  :vann/preferredNamespaceUri "http://rdf/naturallexicon.org/grafter#"
  :dc/description "Supporting ontology for igraph-grafter"
  })

(def ontology-atom (atom (make-graph)))

(defn update-ontology [to-add]
  (swap! ontology-atom add to-add))

(update-ontology
 [[:grafter/Instant
   :rdf/type :grafter/LiteralType
   :rdfs/comment "A dispatch value for Clojure instants"
   ]
  [:grafter/DataTypeURI
   :rdf/type :grafter/LiteralType
   :rdfs/comment "A dispatch value for literals encoded as XSD
   datatypes in the grafter codebase."
   ]
  [:rdf-app/LangStr
   :rdf/type :grafter/LiteralType
   :rdfs/comment "A dispatch value for literals encoded as a language-tagged 
   string"
   ]
  ])


