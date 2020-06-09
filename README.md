# <img src="http://ericdscott.com/NaturalLexiconLogo.png" alt="NaturalLexicon logo" :width=100 height=100/> ont-app/igraph-grafter 

A port of the IGraph protocols to the Grafter protocols.

Part of the ont-app library, dedicated to Ontology-driven development.

At this point it is hosted on the JVM only.

## Contents
- [Motivation](#h2-motivation)
- [Usage](#h2-usage)
- [Keyword Identifiers as URIs](#h2-keyword-identifiers)
- [Literals](#h2-literals)
  - [xsd values](#h3-xsd)
  - [#inst values](#h3-inst-values)
  - [language-tagged-strings](#h3-language-tagged-strings)
  - [Transit-encoding of Clojure containers](#h3-transit-encoding)
- [License](#h2-license)

<a name="h2-motivation"></a>
## Motivation
The people at [Swirrl](https://www.swirrl.com/) have graciously
provided a large body of rdf-centric clojure code on github, largely
centered around a project called
[Grafter](https://github.com/Swirrl/grafter). Grafter is largely a
wrapper around the API for [Eclipse rdf4j](https://rdf4j.org/), which
provides a wide variety of ways to deal with RDF data.

The purpose of this library is to allow connections to grafter-based
RDF model to be viewed under the
[IGraph](https://github.com/ont-app/igraph) protocols, which defines a
generic container type for named relations between named entities.

<a name="h2-usage"></a>
## Usage

Available at clojars:

```
[ont-app/igraph-grafter "0.1.0"]
```

The following code will instantiate a graph:

```
(ns my-namespace
  {
    :vann/preferredNamespacePrefix "myns"
    :vann/preferredNamespaceUri "http://my.uri.com/"
  }
  (require 
    [grafter-2.rdf4j.repository :as repo]
    [ont-app.igraph-grafter.core :as igraph-grafter]
    [ont-app.vocabulary.core :as voc]  
    ))

(def repo (repo/sail-repo))
(def conn (repo/->connection repo))
(def g (igraph-grafter/make-graph conn :myns/MyGraph))
```

The `repo` can be any rdf4j [SAIL (Storage And Inference Layer)](https://rdf4j.org/javadoc/latest/org/eclipse/rdf4j/sail/package-summary.html)
instantiated by the grafter [repository
module](https://cljdoc.org/d/grafter/grafter/2.0.3/api/grafter-2.rdf4j.repository).

The default is an in-memory store.

Use _make-graph_ to create a wrapper around the connection to allow
for IGraph member access methods. Mutability is
[_mutable_](https://github.com/ont-app/igraph#IGraphMutable), meaning
that triples are added and removed with _add!_ and _subtract!_.

```
> (add! g [:myns/Subject :rdf/type :myns/Thing])
> (g :myns/Subject)
{:rdf/type #{myns/Thing}}
>
```

See [ont-app/IGraph](https://github.com/ont-app/igraph) for
full documentation of the IGraph and IGraphMutable protocols.

The original connection can be attained with `(:conn g)`.  The KWI of
the associated named graph can be attained with `(:graph-kwi g)`. This
will give you low-level access to the data set. See the
[Grafter](https://github.com/Swirrl/grafter) project for details.


<a name="h2-keyword-identifiers"></a>
## Keyword Identifiers as URIs

In keeping with the overall approach of the ont-app libraries, URIs
are encoded in clojure as Keyword Identifiers (KWIs), using the
constructs defined in
[ont-app/vocabulary](https://github.com/ont-app/vocabulary).

This library uses metadata attached to Clojure namespaces to define
mappings between namespaced keywords in Clojure code and corresponding
RDF namespaces.

So in the example above, `:myns/Thing` would translate to
`"http://my.uri.com/Thing"`, because of the
_vann:preferredNamespacePrefix_ and _vann/preferredNamespaceUri_
declarations in the metadata of _my-namespace_.

Blank nodes are interned in the namespace `_`. 

```
> (bnode-kwi #object[grafter_2.rdf.protocols.BNode yadda "tablegroupG__21835"]
:_/tablegroupG__21835
>
```

<a name="h2-literals"></a>
### Literals

This library is supported by
[igraph/rdf](https://github.com/ont-app/rdf), which defines a
[_render-literal_](https://github.com/ont-app/rdf#h3-render-literal-multimethod)
multimethod. There are methods for each of the types of literals
described below.

<a name="h3-xsd"></a>
#### xsd values
Grafter has its own logic for dealing with [_xsd_
datatypes](https://en.wikipedia.org/wiki/XML_Schema_(W3C)) for
scalars, and _ont-app/igraph-grafter_ integrates with this directly.

<a name="h3-inst-values"></a>
#### #inst values
Clojure's #inst reader macro is also supported. Its contents are
rendered as a string matching _igraph-grafter/date-time-regex_,
matching the standard format expected by
[_clojure.instant/read-instant-date_](https://clojuredocs.org/clojure.instant/read-instant-date).
For example, `#inst "2000"` is translated as
"2000-01-01T00:00:00Z". Such strings will be matched and instantiated
as #inst expressions.

<a name="h3-language-tagged-strings"></a>
#### language-tagged strings
A `#lstr` reader macro is defined to support [language-tagged
strings](https://github.com/ont-app/vocabulary/#h2-language-tagged-strings)
in clojue code, For example, `#lstr "gaol@en-GB"` in clojure code will
translate to RDF `"gaol"@en-GB`. Conversely, data imported into a
graph from RDF will be translated using the same reader macro.

<a name="h3-transit-encoding"></a>
#### Transit-encoding of clojure containers
Clojure's standard container classes when provided as literal RDF
objects are [encoded in RDF as
transit](https://github.com/ont-app/rdf#h3-transit-encoded-values),
and decoded transparently.

So for example the vector `[1 2 3]` would be encoded in the RDF store
as `"[1,2,3]"^^transit:json`. When read back in again, it will be
reconsituted as the original vector.

This works because clojure's vector type has a clojure
[_derive_](https://clojuredocs.org/clojure.core/derive) statement to
the dispatch value _:rdf-app/TransitData_, whose _render-literal_
method does the rendering.

```
(derive clojure.lang.PersistentVector :rdf-app/TransitData)
```

If you prefer that a vector or some other clojure composite data
structure be handled some other way, this declaration can be reversed
with the [_underive_
](https://clojuredocs.org/clojure.core/underive)function.

See the documentation of [ont-app/rdf](https://github.com/ont-app/rdf)
for more details.

<a name="h2-license"></a>
## License

Copyright © 2020 FIXME

This program and the accompanying materials are made available under
the terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following
Secondary Licenses when the conditions for such availability set forth
in the Eclipse Public License, v. 2.0 are satisfied: GNU General
Public License as published by the Free Software Foundation, either
version 2 of the License, or (at your option) any later version, with
the GNU Classpath Exception which is available at
https://www.gnu.org/software/classpath/license.html.


<table> <tr> <td width=75> <img
src="http://ericdscott.com/NaturalLexiconLogo.png" alt="Natural
Lexicon logo" :width=50 height=50/> </td> <td> <p>Natural Lexicon
logo - Copyright © 2020 Eric D. Scott. Artwork by Athena M. Scott.</p>
<p>Released under <a
href="https://creativecommons.org/licenses/by-sa/4.0/">Creative
Commons Attribution-ShareAlike 4.0 International license</a>. Under
the terms of this license, if you display this logo or derivates
thereof, you must include an attribution to the original source, with
a link to https://github.com/ont-app, or http://ericdscott.com. </p>
</td> </tr> <table>
