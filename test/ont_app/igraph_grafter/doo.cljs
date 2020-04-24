(ns ont-app.igraph-grafter.doo
  (:require [doo.runner :refer-macros [doo-tests]]
            [ont-app.igraph-grafter.core-test]
            ))

(doo-tests
 'ont-app.igraph-grafter.core-test
 )
