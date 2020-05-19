# ont-app/igraph-grafter

A port of the IGraph protocols to the Grafter protocols.

Part of the ont-app library, dedicated to Ontology-driven development.

## Usage

```
(ns my-namespace
  (require 
    [grafter-2.rdf4j.repository :as repo]
    [ont-app.igraph-grafter.core :as igraph-grafter]
    [ont-app.vocabulary.core :as voc]  
    ))

(voc/put-ns-meta!
  'my-namespace
  {
    :vann/preferredNamespacePrefix "myns"
    :vann/preferredNamespaceUri "http://my.uri.com/"
  })
  
(def repo (repo/sail-repo))
(def conn (repo/->connection repo))
(def g (igraph-grafter/make-graph conn :myns/MyGraph))
```

The `repo` can by any rdf4j SAIL (Storage And Inference Layer)
instantiated by the grafter [repository
module](https://cljdoc.org/d/grafter/grafter/2.0.3/api/grafter-2.rdf4j.repository).

The default is an in-memory store.

Use _make-graph_ to create a wrapper around the connection to allow
for IGraph member access methods. Mutability is _mutable_, meaning
that triples are added and removed with _add!_ and _subtract!_.

```
> (add! g [:myns/Subject :rdf/type :myns/Thing])
```

See [ont-app/IGraph](https://github.com/ont-app/igraph) for documenation.

The original connection can be attained with `(:conn g)`.
The KWI of the associated named graph can be attained with `(:graph-kwi g)`.

### Keyword Identifiers as URIs

In keeping with the overall approach of the ont-app libraries, URIs
are encoded in clojure as Keyword Identifiers (KWIs), using the method
defined in
[ont-app/vocabulary](https://github.com/ont-app/vocabulary).

This library uses metadata attached to Clojure namespaces to define mappings between namespaced keywords in Clojure code and corresponding RDF namespaces.

### Literals

This library is supported by
[igraph/rdf](https://github.com/ont-app/rdf), which defines a
_render-literal_ multimethod.

#### xsd
Grafter has its own logic for dealing with _xsd_ datatypes for
scalars, and this library follows this.

#### language-tagged strings
A #lstr reader macro is defined. For example, `#lstr "gaol@en-GB"` in
clojure code will translate to RDF `"gaol"@en-GB`.

#### #inst values
Clojure's #inst reader macro is also supported. Its contents are
rendered as a string matching _igraph-grafter/date-time-regex_,
matching the standard format expected by
_clojure.instant/read-instant-date_.  For example, `#inst "2000"` is
translated as "2000-01-01T00:00:00Z". Such strings will be matched and
instantiated as #inst expressions.

#### Transit-encoding of clojure containers
Clojure's standard container classes when provided as literal RDF
objects are encoded as transit, and decoded transparently. 

So the vector `[1 2 3]` would be encoded in the RDF store as
`"[1,2,3]"^^transit:json`. When read back in again, it will be
reconsituted as the original vector.

This works because the data types have _derive_ statements to the
dispatch value for the dispatch value _:rdf-app/TransitData_, whose
method does the rendering.

```
(derive clojure.lang.PersistentVector :rdf-app/TransitData)
```

If you prefer that vectors and such be handled some other way, this
declaration can be reversed with the _underive_ function.

See the documentation of ont-app/rdf for more details.


## License

Copyright © 2020 FIXME

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
