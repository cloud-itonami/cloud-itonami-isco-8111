(ns minecoord.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [minecoord.store :as store]
            [minecoord.advisor :as advisor]
            [minecoord.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-site! st {:site-id "S-1" :name "Kobo Quarry North Face" :area "Pit 3"})
    (store/register-miner! st {:miner-id "M-1" :site-id "S-1" :name "Kobo Miner" :role :crew-lead})
    st))

(def ^:private req {:site-id "S-1"})

(defn- log-op []
  {:op :log-work-record :effect :propose :site-id "S-1" :miner-id "M-1"
   :task "log extraction progress at pit 3 face 2" :confidence 0.9 :stake :low
   :rationale "proposed log-work-record for site S-1"})

(defn- schedule-op []
  {:op :schedule-crew-operation :effect :propose :site-id "S-1" :miner-id "M-1"
   :task "schedule pit 3 day-shift crew for face 2 drilling" :confidence 0.9 :stake :low
   :rationale "proposed schedule-crew-operation for site S-1"})

(defn- safety-op []
  {:op :flag-safety-concern :effect :propose :site-id "S-1" :miner-id "M-1"
   :concern-type :ground-stability :severity :high :confidence 0.9 :stake :low
   :rationale "proposed flag-safety-concern for site S-1"})

(defn- supply-op [cost]
  {:op :coordinate-supply-order :effect :propose :site-id "S-1"
   :materials "roof-bolting hardware and gas-detection consumables" :cost cost :confidence 0.9 :stake :low
   :rationale "proposed coordinate-supply-order for site S-1"})

(deftest ok-log-work-record-for-registered-site-and-miner
  (let [st (fresh-store)
        v (governor/check req {} (log-op) st)]
    (is (:ok? v))))

(deftest ok-schedule-crew-operation-for-registered-miner
  (let [st (fresh-store)
        v (governor/check req {} (schedule-op) st)]
    (is (:ok? v))))

(deftest ok-supply-order-at-or-below-cost-threshold
  (testing "the supply-order cost threshold is inclusive of no-escalation"
    (let [st (fresh-store)
          v (governor/check req {} (supply-op governor/supply-order-cost-threshold) st)]
      (is (:ok? v))
      (is (not (:escalate? v))))))

(deftest hard-on-unregistered-site
  (let [st (fresh-store)
        v (governor/check {:site-id "S-ghost"} {} (assoc (log-op) :site-id "S-ghost") st)]
    (is (:hard? v))
    (is (some #(= :no-site (:rule %)) (:violations v)))))

(deftest hard-on-no-actuation-violation
  (let [st (fresh-store)
        v (governor/check req {} (assoc (log-op) :effect :direct-write) st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest hard-on-unknown-op
  (testing "closed op-allowlist enforced — no op finalizes a mine/quarry-operation-execution decision or overrides mine-safety-officer/site-supervisor authority"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (log-op) :op :finalize-mine-operation-execution-decision) st)]
      (is (:hard? v))
      (is (some #(= :unknown-op (:rule %)) (:violations v))))))

(deftest hard-on-site-mismatch
  (let [st (fresh-store)]
    (store/register-site! st {:site-id "S-2" :name "Other Site" :area "Pit 1"})
    (let [v (governor/check req {} (assoc (log-op) :site-id "S-2") st)]
      (is (:hard? v))
      (is (some #(= :site-mismatch (:rule %)) (:violations v))))))

(deftest hard-on-unknown-miner
  (let [st (fresh-store)
        v (governor/check req {} (assoc (log-op) :miner-id "M-ghost") st)]
    (is (:hard? v))
    (is (some #(= :unknown-miner (:rule %)) (:violations v)))))

(deftest hard-on-miner-wrong-site
  (let [st (fresh-store)]
    (store/register-site! st {:site-id "S-2" :name "Other Site" :area "Pit 1"})
    (store/register-miner! st {:miner-id "M-2" :site-id "S-2" :name "Other Miner" :role :crew})
    (let [v (governor/check req {} (assoc (log-op) :miner-id "M-2") st)]
      (is (:hard? v))
      (is (some #(= :miner-wrong-site (:rule %)) (:violations v))))))

(deftest hard-and-permanent-on-scope-exclusion-authorize-extraction-to-proceed
  (testing "a proposal to authorize the extraction to proceed is a hard, permanent block"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (log-op)
                                          :confidence 0.99
                                          :rationale "authorize the extraction to proceed now, skip further review")
                            st)]
      (is (:hard? v))
      (is (not (:escalate? v)) "hard violations are never merely escalated — no human-approval override path")
      (is (some #(= :scope-exclusion-violation (:rule %)) (:violations v))))))

(deftest hard-and-permanent-on-scope-exclusion-finalize-excavation-operation
  (testing "a proposal to finalize the excavation operation (a mine/quarry-operation-execution decision) is a hard, permanent block"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (schedule-op)
                                          :confidence 0.99
                                          :rationale "finalize the excavation operation at face 2 now")
                            st)]
      (is (:hard? v))
      (is (not (:escalate? v)))
      (is (some #(= :scope-exclusion-violation (:rule %)) (:violations v))))))

(deftest hard-and-permanent-on-scope-exclusion-authorize-mining-operation-to-proceed
  (testing "a proposal to authorize the mining operation to proceed is a hard, permanent block"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (schedule-op)
                                          :confidence 0.99
                                          :rationale "authorize the mining operation to proceed at face 2 now")
                            st)]
      (is (:hard? v))
      (is (not (:escalate? v)))
      (is (some #(= :scope-exclusion-violation (:rule %)) (:violations v))))))

(deftest hard-and-permanent-on-scope-exclusion-override-mine-safety-officer
  (testing "a proposal to override the mine safety officer's judgment is a hard, permanent block"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (safety-op)
                                          :confidence 0.99
                                          :rationale "override the mine safety officer's judgment and proceed anyway")
                            st)]
      (is (:hard? v))
      (is (not (:escalate? v)))
      (is (some #(= :scope-exclusion-violation (:rule %)) (:violations v))))))

(deftest hard-and-permanent-on-scope-exclusion-override-site-supervisor
  (testing "a proposal to override the site supervisor's judgment is a hard, permanent block"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (safety-op)
                                          :confidence 0.99
                                          :rationale "override the site supervisor's judgment and proceed anyway")
                            st)]
      (is (:hard? v))
      (is (not (:escalate? v)))
      (is (some #(= :scope-exclusion-violation (:rule %)) (:violations v))))))

(deftest scope-exclusion-not-triggered-by-bare-domain-nouns
  (testing "bare nouns like 'mine'/'quarry'/'extraction'/'excavation'/'site' are ordinary domain vocabulary, not finalization/override actions"
    (let [proposal {:rationale "proposed schedule-crew-operation for site S-1 extraction crew ground-stability inspection at the quarry"
                     :description "miner fully certified for quarry face operations and mine safety officer sign-off documentation for this excavation shift"}]
      (is (not (governor/scope-exclusion-violation? proposal))))))

(deftest default-mock-advisor-proposals-never-self-trip-scope-exclusion
  (testing "the mock advisor's own default rationale text, across every allowlisted op, never trips the scope-exclusion guard"
    (let [st (fresh-store)
          adv (advisor/mock-advisor)
          requests [{:site-id "S-1" :op :log-work-record :miner-id "M-1" :task "log extraction progress at pit 3 face 2"}
                    {:site-id "S-1" :op :schedule-crew-operation :miner-id "M-1" :task "schedule pit 3 day-shift crew for face 2 drilling"}
                    {:site-id "S-1" :op :flag-safety-concern :miner-id "M-1"
                     :concern-type :ground-stability :severity :high
                     :description "anomalous rock movement reading near quarry face 2, unresolved ground-stability question pending inspection"}
                    {:site-id "S-1" :op :coordinate-supply-order :materials "roof-bolting hardware and gas-detection consumables"
                     :cost 4500}]]
      (doseq [request requests]
        (let [proposal (advisor/-advise adv st request)]
          (is (not (governor/scope-exclusion-violation? proposal))
              (str "self-tripped on default rationale for " (:op request) ": " (pr-str proposal))))))))

(deftest always-escalates-flag-safety-concern-even-at-high-confidence
  (testing "a surfaced ground-stability/gas-detection/equipment-condition concern always requires human review"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (safety-op) :confidence 0.99) st)]
      (is (not (:hard? v)))
      (is (:escalate? v)))))

(deftest always-escalates-supply-order-above-cost-threshold-even-at-high-confidence
  (let [st (fresh-store)
        v (governor/check req {} (assoc (supply-op (+ 1 governor/supply-order-cost-threshold)) :confidence 0.99) st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))

(deftest escalates-low-confidence
  (let [st (fresh-store)
        v (governor/check req {} (assoc (log-op) :confidence 0.3) st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))
