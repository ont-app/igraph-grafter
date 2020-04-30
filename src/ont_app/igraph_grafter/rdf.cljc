(ns ont-app.igraph-grafter.rdf
  (:require
   [clojure.string :as s]
   [clojure.java.io :as io]
   [clojure.spec.alpha :as spec]
   ;; 3rd party
   [selmer.parser :as selmer]
   [taoensso.timbre :as timbre]
   [cognitect.transit :as transit]
   ;; ont-app
   [ont-app.graph-log.core :as glog]
   [ont-app.graph-log.levels :as levels :refer :all]
   ;; [ont-app.sparql-endpoint.core :as endpoint]
   [ont-app.igraph.core :as igraph :refer :all]
   [ont-app.igraph.graph :as graph]
   ;; [ont-app.sparql-client.ont :as ont]
   [ont-app.vocabulary.core :as voc]

   )
  
  )

(def prefixed voc/prepend-prefix-declarations)

(defn quote-str [s]
  "Returns `s`, in excaped quotation marks.
Where
<s> is a string, typically to be rendered in a query or RDF source.
"
  (value-trace
   ::QuoteString
   (str "\"" s "\"")
   ))

(def special-literal-dispatch
  "A function [x] -> <dispatch-value>
  Where
  <x> is any value, probabaly an RDF literal
  <dispatch-value> is a value to be matched to a render-literal-dispatch method.
  Default is to return nil."
  (atom (fn [_] nil)))

(defn render-literal-dispatch
  "Returns a key for the render-literal method to dispatch on given `literal`
  Where
  <literal> is any non-keyword
  NOTE: ::instant and ::xsd-type are special cases, otherwise (type <literal>)
  "
  [literal]
  (value-trace
   ::RenderLiteralDispatch
   [:log/iteral literal]
   (if-let [special-dispatch (@special-literal-dispatch literal)]
     special-dispatch
     ;; else no special dispatch...
   (cond
     ;; (inst? literal) ::instant
     ;; (endpoint/xsd-type-uri literal) ::xsd-type
     :default (type literal)))))

(defmulti render-literal
  "Returns an RDF (Turtle) rendering of `literal`"
  render-literal-dispatch)


;; (defmethod render-literal ::instant
;;   [instant]
;;   (let [xsd-uri (endpoint/xsd-type-uri
;;                  (if (not (instance? java.time.Instant instant))
;;                    (.toInstant instant)
;;                    instant))
;;         ]
;;     (str (quote-str (.toInstant instant))
;;          "^^"
;;          (voc/qname-for (kwi-for xsd-uri)))))

;; (defmethod render-literal ::xsd-type
;;   [xsd-value]
;;   (let [xsd-uri (endpoint/xsd-type-uri xsd-value)]
;;     (str (quote-str xsd-value) "^^" (voc/qname-for (kwi-for xsd-uri)))))


;; (defmethod render-literal (type #langStr "@en")
;;   [lang-str]
;;   (str (quote-str (str lang-str)) "@" (endpoint/lang lang-str)))

;; (defmethod render-literal (type [])
;;   [v]
;;   (render-literal-as-transit-json v))

;; (defmethod render-literal (type {})
;;   [m]
;;   (render-literal-as-transit-json m))

;; (defmethod render-literal (type '(nil))
;;   [s]
;;   (render-literal-as-transit-json s))


(defmethod render-literal :default
  [s]
  (quote-str s)
  )


(defn bnode-kwi?
  "True when `kwi` matches output of `bnode-translator`."
  [kwi]
  (->> (namespace kwi)
       (re-matches #"^_.*")))

(spec/def ::bnode-kwi bnode-kwi?)


(defn- query-template-map [graph-uri-fn client]
  "Returns {<k> <v>, ...} appropriate for <client>
Where
<k> and <v> are selmer template parameters which may appear in some query, e.g.
  named graph open/close clauses
<client> is a ::sparql-client
"
  {:graph-name-open (if-let [graph-uri (graph-uri-fn client)]
                      (str "GRAPH <" (voc/iri-for graph-uri) "> {")
                      "")
   :graph-name-close (if-let [graph-uri (graph-uri-fn client)]
                      (str "}")
                      "")
   })                          

(def subjects-query-template
  "
  Select Distinct ?s Where
  {
    {{graph-name-open|safe}}
    ?s ?p ?o.
    {{graph-name-close|safe}}
  }
  ")

(defn query-for-subjects 
  "Returns [<subject> ...] at endpoint of `client`
Where
<subject> is the uri of a subject from <client>, 
  rendered per the binding translator of <client>
<client> conforms to ::sparql-client spec
"
  ([query-fn client]
   (query-for-subjects (fn [_] nil) query-fn client)
   )
  
  ([graph-uri-fn query-fn client]
   
   (let [query (selmer/render subjects-query-template
                              (query-template-map graph-uri-fn client))
         ]
     (map :s
          (query-fn client query)))))

(def normal-form-query-template
  "
  Select ?s ?p ?o
  Where
  {
    {{graph-name-open|safe}}
    ?s ?p ?o
    {{graph-name-close}}
  }
  ")

(defn query-for-normal-form
  ([query-fn client]
   (query-for-normal-form (fn [_] nil) query-fn client))
  
  ([graph-uri-fn query-fn client]
   (letfn [(maybe-kwi-for [x]
             (if (uri? x)
               (voc/keyword-for (str x))))
           (add-o [o binding]
            (conj o (maybe-kwi-for (:o binding))))
          (add-po [po binding]
            (assoc po (voc/keyword-for (str (:p binding)))
                   (add-o (get po (:p binding) #{})
                          binding)))
          (collect-binding [spo binding]
            (assoc spo (voc/keyword-for (str (:s binding)))
                   (add-po (get spo (:s binding) {})
                           binding)))
          
          ]
    (let [query (selmer/render normal-form-query-template
                               (query-template-map graph-uri-fn client))
          ]
      (reduce collect-binding {}
              (query-fn client query))))))


(defn check-ns-metadata 
  "Logs a warning when `kwi` is in a namespace with no metadata."
  [kwi]
  (let [n (symbol (namespace kwi))]
    (if-let [the-ns (find-ns n)]
      (when (not (meta the-ns))
        (warn ::NoMetaDataInNS
              :glog/message "The namespace for {{log/kwi}} is in a namespace with no associated metadata."
              :log/kwi kwi))))
  kwi)


(defn check-qname [uri-spec]
  "Traps the keyword assertion error in voc and throws a more meaningful error about blank nodes not being supported as first-class identifiers."
  (if (bnode-kwi? uri-spec)
    uri-spec
    ;;else not a blank node
    (try
      (voc/qname-for (check-ns-metadata uri-spec))
      (catch java.lang.AssertionError e
        (if (= (str e)
               "java.lang.AssertionError: Assert failed: (keyword? kw)")
          (throw (ex-info (str "The URI spec " uri-spec " is not a keyword.\nCould it be a blank node?\nIf so, blank nodes cannot be treated as first-class identifiers in SPARQL. Use a dedicated query that traverses the blank node instead.")
                          (merge (ex-data e)
                                 {:type ::Non-Keyword-URI-spec
                                  ::uri-spec uri-spec
                                  })))
                             
          ;; else it's some other message
          (throw e))))))

(def query-for-p-o-template
  "
  Select ?p ?o Where
  {
    {{graph-name-open|safe}}
    {{subject|safe}} ?p ?o.
    {{graph-name-close|safe}}
  }
  ")

(defn query-for-p-o 
  "Returns {<p> #{<o>...}...} for `s` at endpoint of `client`
Where
<p> is a predicate URI rendered per binding translator of <client>
<o> is an object value, rendered per the binding translator of <client>
<s> is a subject uri keyword. ~ voc/voc-re
<client> conforms to ::sparql-client
"
  ([query-fn client s]
   (query-for-p-o (fn [_] nil) query-fn client s)
   )
  (
  [graph-uri-fn query-fn client s]
  (let [query  (prefixed
                (selmer/render query-for-p-o-template
                               (merge (query-template-map graph-uri-fn client)
                                      {:subject (check-qname s)})))
        collect-bindings (fn [acc b]
                           (update acc (:p b)
                                   (fn[os] (set (conj os (:o b))))))
                                                
        ]
    (value-debug
     ::query-for-po
     [::query query ::subject s]
     (reduce collect-bindings {}
             (query-fn client query))))))


(def query-for-o-template
  "
  Select ?o Where
  {
    {{graph-name-open|safe}}
    {{subject|safe}} {{predicate|safe}} ?o.
    {{graph-name-close|safe}}
  }
  ")

(defn query-for-o 
  "Returns #{<o>...} for `s` and `p` at endpoint of `client`
Where:
<o> is an object rendered per binding translator of <client>
<s> is a subject URI rendered per binding translator of <client>
<p> is a predicate URI rendered per binding translator of <client>
<client> conforms to ::sparql-client
"
  ([query-fn client s p]
   (query-for-o (fn [_] nil) query-fn client s p))
  
  ([graph-uri-fn query-fn client s p]
   (let [query  (prefixed
                 (selmer/render
                  query-for-o-template
                  (merge (query-template-map graph-uri-fn client)
                         {:subject (check-qname s)
                          :predicate (check-qname p)})))
        
         collect-bindings (fn [acc b]
                            (conj acc (:o b)))
                                                
        ]
     (value-debug
      ::query-for-o-return
      [::query query
       ::subject s
       ::predicate p]
      (reduce collect-bindings #{}
              (query-fn client query))))))

(def ask-s-p-o-template
  "ASK where
  {
    {{graph-name-open|safe}}
    {{subject|safe}} {{predicate|safe}} {{object|safe}}.
    {{graph-name-close}}
  }"
  )


(defn ask-s-p-o 
  "Returns true if `s` `p` `o` is a triple at endpoint of `client`
Where:
<s> <p> <o> are subject, predicate and object
<client> conforms to ::sparql-client
"
  ([ask-fn client s p o]
   (ask-s-p-o (fn [_] nil) ask-fn client s p o)
   )
  ([graph-uri-fn ask-fn client s p o]
  
  (let [query (prefixed
               (selmer/render
                ask-s-p-o-template
                (merge (query-template-map graph-uri-fn client)
                       {:subject (check-qname s)
                        :predicate (check-qname p)
                        :object (if (keyword? o)
                                  (voc/qname-for o)
                                  (render-literal o))})))
        starting (debug ::Starting_ask-s-p-o
                        :log/query query
                        :log/subject s
                        :log/predicate p
                        :log/object o)
        ]
    (value-debug
     ::ask-s-p-o-return
     [:log/resultOf starting]
     (ask-fn client query)))))