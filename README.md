# kotoba-lang/identify

[![CI](https://github.com/kotoba-lang/identify/actions/workflows/ci.yml/badge.svg)](https://github.com/kotoba-lang/identify/actions/workflows/ci.yml)

Identifier→identity resolution ("who is this") over
[`kotoba-lang/identity`](https://github.com/kotoba-lang/identity)'s record
shape. `IIdentityStore` is the seam a host's real user store implements
(Postgres, Datomic, whatever); `mock-identity-store` here is a deterministic
in-memory implementation for tests and demos, mirroring
[`kotoba-lang/godaddy-dns`](https://github.com/kotoba-lang/godaddy-dns)'s
`IDns`/`mock-dns` pattern. Every namespace is `.cljc`, zero third-party
runtime deps beyond `identity.core` itself.

## Usage

```clojure
(require '[identify.core :as identify])

(def store (identify/mock-identity-store))

(identify/resolve-or-create! store
  {:type :email :value "Alice@Example.com"
   :attributes {:name "Alice"}
   :id-fn (fn [] (str (random-uuid)))})
;; => {:identity {...} :created? true}

;; same email, any casing/whitespace, resolves to the same identity:
(identify/resolve-or-create! store
  {:type :email :value " alice@example.com "
   :id-fn (fn [] (str (random-uuid)))})
;; => {:identity {...} :created? false}
```

`normalize-identifier` handles per-type normalization (`:email` lowercased
+ trimmed, `:phone` stripped to a leading `+` and digits) so a login flow
never accidentally creates two identities for the same person because of
casing or formatting differences.

## Dependency on `kotoba-lang/identity`

`deps.edn` pins a git SHA of `kotoba-lang/identity` for CI and normal
consumers, plus a `:local` alias overriding it to `{:local/root
"../identity"}` for local development against a sibling checkout — the
same split [`kotoba-lang/drawingml`](https://github.com/kotoba-lang/drawingml)
uses for its `xml` dependency.

## Test

```bash
# against the pinned git sha
clojure -M:test
# against a local sibling ../identity checkout
clojure -M:local:test
```
