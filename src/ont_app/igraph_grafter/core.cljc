(ns ont-app.igraph-grafter.core
    (:require
     [ont-app.igraph-grafter.ont :as ont]
     ))

(voc/cljc-put-ns-meta!
 'ont-app.igraph-grafter.core
 {
  :voc/mapsTo 'ont-app.igraph-grafter.ont
  }
 )

(def ontology ont/ontology)

;; FUN WITH READER MACROS

#?(:cljs
   (enable-console-print!)
   )

#?(:cljs
   (defn on-js-reload [] )
   )

;; NO READER MACROS BEYOND THIS POINT

(defn dummy-fn []
  "The eagle has landed")
    
