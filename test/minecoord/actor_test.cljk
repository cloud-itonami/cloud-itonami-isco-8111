(ns minecoord.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [minecoord.actor :as actor]
            [minecoord.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-site! st {:site-id "S-1" :name "Kobo Quarry North Face" :area "Pit 3"})
    (store/register-miner! st {:miner-id "M-1" :site-id "S-1" :name "Kobo Miner" :role :crew-lead})
    st))

(deftest commits-a-registered-miner-log-work-record
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:site-id "S-1" :op :log-work-record :stake :low
                 :miner-id "M-1" :task "log extraction progress at pit 3 face 2"}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/records-of st "S-1"))))))

(deftest commits-a-crew-scheduling-proposal
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:site-id "S-1" :op :schedule-crew-operation :stake :low
                 :miner-id "M-1" :task "schedule pit 3 day-shift crew for face 2 drilling"}
        result (actor/run-request! graph request {} "thread-sched")]
    (is (= :done (:status result)))
    (is (= 1 (count (store/records-of st "S-1"))))))

(deftest holds-an-unregistered-site-request
  (testing "the mine/quarry site record must be independently verified/registered before any action"
    (let [st (fresh-store)
          graph (actor/build-graph {:store st})
          request {:site-id "S-ghost" :op :log-work-record :stake :low
                   :miner-id "M-1" :task "log extraction progress at pit 3 face 2"}
          result (actor/run-request! graph request {} "thread-2")]
      (is (= :hold (:disposition (:state result))))
      (is (empty? (store/records-of st "S-ghost"))))))

(deftest holds-a-scope-excluded-proposal-with-no-interrupt-path
  (testing "a proposal to authorize the extraction to proceed is a hard, permanent block — never routed through :request-approval"
    (let [st (fresh-store)
          graph (actor/build-graph {:store st})
          request {:site-id "S-1" :op :log-work-record :stake :low
                   :miner-id "M-1" :task "log extraction progress at pit 3 face 2"
                   :description "authorize the extraction to proceed now, skip further review"}
          result (actor/run-request! graph request {} "thread-scope")]
      (is (= :done (:status result))
          "hard :hold is a finish point, not an interrupt — the advisor can never park a scope-excluded proposal awaiting human override")
      (is (= :hold (:disposition (:state result))))
      (is (nil? (get-in result [:state :record])))
      (is (empty? (store/records-of st "S-1"))))))

(deftest holds-a-finalize-excavation-operation-proposal-with-no-interrupt-path
  (testing "a proposal to finalize the excavation operation (a mine/quarry-operation-execution decision) is a hard, permanent block — never routed through :request-approval"
    (let [st (fresh-store)
          graph (actor/build-graph {:store st})
          request {:site-id "S-1" :op :schedule-crew-operation :stake :low
                   :miner-id "M-1" :task "schedule pit 3 day-shift crew for face 2 drilling"
                   :description "finalize the excavation operation at face 2 now"}
          result (actor/run-request! graph request {} "thread-finalize-excavation")]
      (is (= :done (:status result)))
      (is (= :hold (:disposition (:state result))))
      (is (nil? (get-in result [:state :record])))
      (is (empty? (store/records-of st "S-1"))))))

(deftest holds-an-override-mine-safety-officer-proposal-with-no-interrupt-path
  (testing "a proposal to override the mine safety officer's judgment is a hard, permanent block — never routed through :request-approval"
    (let [st (fresh-store)
          graph (actor/build-graph {:store st})
          request {:site-id "S-1" :op :flag-safety-concern :stake :low
                   :miner-id "M-1" :concern-type :ground-stability :severity :high
                   :description "override the mine safety officer's judgment and proceed anyway"}
          result (actor/run-request! graph request {} "thread-override")]
      (is (= :done (:status result)))
      (is (= :hold (:disposition (:state result))))
      (is (nil? (get-in result [:state :record])))
      (is (empty? (store/records-of st "S-1"))))))

(deftest interrupts-then-approves-a-safety-concern-flag-on-human-approval
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:site-id "S-1" :op :flag-safety-concern :stake :low
                 :miner-id "M-1" :concern-type :ground-stability :severity :high}
        interrupted (actor/run-request! graph request {} "thread-3")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "S-1")))
    (let [resumed (actor/approve! graph "thread-3")]
      (is (= :done (:status resumed)))
      (is (= 1 (count (store/records-of st "S-1")))))))

(deftest interrupts-then-approves-an-above-threshold-supply-order-on-human-approval
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:site-id "S-1" :op :coordinate-supply-order :stake :low
                 :materials "roof-bolting hardware and gas-detection consumables" :cost 25000}
        interrupted (actor/run-request! graph request {} "thread-4")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "S-1")))
    (let [resumed (actor/approve! graph "thread-4")]
      (is (= :done (:status resumed)))
      (is (= 1 (count (store/records-of st "S-1")))))))
