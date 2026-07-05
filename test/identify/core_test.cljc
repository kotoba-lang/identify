(ns identify.core-test
  (:require [clojure.test :refer [deftest testing is]]
            [identify.core :as identify]
            [identity.core :as identity]))

(deftest normalize-identifier-test
  (testing "email lowercased + trimmed"
    (is (= "alice@example.com" (identify/normalize-identifier :email "  Alice@Example.COM "))))
  (testing "phone strips everything but a leading + and digits"
    (is (= "+15551234567" (identify/normalize-identifier :phone "+1 (555) 123-4567")))
    (is (= "5551234567" (identify/normalize-identifier :phone "555.123.4567"))))
  (testing "other types are unchanged"
    (is (= "did:key:z6Mk..." (identify/normalize-identifier :did "did:key:z6Mk...")))))

(deftest resolve-or-create-test
  (testing "creates when not found"
    (let [store (identify/mock-identity-store)
          {:keys [identity created?]}
          (identify/resolve-or-create! store {:type :email :value "Alice@Example.com"
                                                :attributes {:name "Alice"}
                                                :id-fn (constantly "u1")})]
      (is created?)
      (is (= "u1" (:id identity)))
      (is (= {:name "Alice"} (:attributes identity)))
      (is (identity/find-identifier identity :email))
      (is (= "alice@example.com" (:value (identity/find-identifier identity :email))))))
  (testing "finds when already present, regardless of input casing/formatting"
    (let [seed (-> (identity/new-identity {:id "u1"})
                   (identity/add-identifier (identity/identifier :email "alice@example.com")))
          store (identify/mock-identity-store [seed])
          {:keys [identity created?]}
          (identify/resolve-or-create! store {:type :email :value " ALICE@EXAMPLE.COM "
                                                :id-fn (constantly "should-not-be-used")})]
      (is (not created?))
      (is (= "u1" (:id identity)))))
  (testing "second resolve-or-create! for the same identifier does not create a duplicate"
    (let [store (identify/mock-identity-store)
          id-fn (let [calls (atom 0)] (fn [] (str "u" (swap! calls inc))))
          first-result (identify/resolve-or-create! store {:type :email :value "a@b.com" :id-fn id-fn})
          second-result (identify/resolve-or-create! store {:type :email :value "a@b.com" :id-fn id-fn})]
      (is (:created? first-result))
      (is (not (:created? second-result)))
      (is (= (:id (:identity first-result)) (:id (:identity second-result)))))))

(deftest link-identifier-test
  (let [store (identify/mock-identity-store)
        base (identity/new-identity {:id "u1"})
        with-email (identify/link-identifier! store base :email "a@b.com")
        linked (identify/link-identifier! store with-email :phone "+1 555 000 1111")]
    (is (identity/find-identifier linked :email))
    (is (identity/find-identifier linked :phone))
    (testing "persisted in the store"
      (is (= linked (identify/-find-by-identifier store :phone "+15550001111"))))))
