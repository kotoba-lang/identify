(ns identify.core
  "Identifier→identity resolution — the seam a host's real user store
  implements; `mock-identity-store` here is deterministic in-memory for
  tests, mirroring `godaddy-dns`'s `IDns`/`mock-dns`."
  (:require [identity.core :as identity]
            [clojure.string :as str]))

(defn normalize-identifier
  "Per-type normalization: :email lowercased+trimmed, :phone stripped to a
  leading '+' and digits, any other type unchanged. Returns a string."
  [type value]
  (case type
    :email (str/lower-case (str/trim value))
    :phone (let [v (str/trim value)
                 plus (when (str/starts-with? v "+") "+")
                 digits (str/replace v #"[^0-9]" "")]
             (str plus digits))
    value))

(defprotocol IIdentityStore
  (-find-by-identifier [this type value]
    "Returns the identity map with a matching normalized identifier, or nil.")
  (-save! [this identity]
    "Upserts by :id, returns the saved identity."))

(deftype MockIdentityStore [state]
  IIdentityStore
  (-find-by-identifier [_ type value]
    (let [norm (normalize-identifier type value)]
      (some (fn [ident]
              (when (some (fn [i] (and (= type (:type i))
                                        (= norm (normalize-identifier type (:value i)))))
                          (:identifiers ident))
                ident))
            (vals @state))))
  (-save! [_ identity]
    (swap! state assoc (:id identity) identity)
    identity))

(defn mock-identity-store
  "A deterministic in-memory IIdentityStore, optionally seeded from a coll
  of identity maps (keyed by :id)."
  [& [seed-identities]]
  (->MockIdentityStore (atom (into {} (map (fn [i] [(:id i) i])) seed-identities))))

(defn resolve-or-create!
  "Normalizes `value`, looks it up in `store`. Found → {:identity :created?
  false}. Not found → builds a fresh identity via `identity/new-identity`
  (using `(id-fn)` for the new :id — a required injected fn; this library
  never invents random IDs) with one identifier and `attributes`, saves it,
  returns {:identity :created? true}."
  [store {:keys [type value attributes id-fn]}]
  (let [norm (normalize-identifier type value)]
    (if-let [found (-find-by-identifier store type norm)]
      {:identity found :created? false}
      (let [fresh (-> (identity/new-identity {:id (id-fn) :attributes attributes})
                       (identity/add-identifier (identity/identifier type norm)))]
        {:identity (-save! store fresh) :created? true}))))

(defn link-identifier!
  "Normalizes `value`, adds it as an identifier of `existing-identity`,
  persists via `-save!`, returns the updated identity."
  [store existing-identity type value]
  (let [norm (normalize-identifier type value)
        updated (identity/add-identifier existing-identity (identity/identifier type norm))]
    (-save! store updated)))
