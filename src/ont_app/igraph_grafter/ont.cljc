(ns ont-app.igraph-grafter.ont
  (:require
   ;;
   [ont-app.igraph.core :as igraph :refer [add]]
   [ont-app.igraph.graph :as g :refer [make-graph]]
   [ont-app.igraph-vocabulary.core :as igv]
   [ont-app.vocabulary.core :as voc]
   )
  )


(def ontology-atom (atom (make-graph)))

(defn update-ontology [to-add]
  (swap! ontology-atom add to-add))

