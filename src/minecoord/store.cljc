(ns minecoord.store
  "SSoT for the ISCO-08 8111 miners and quarriers mine/quarry-site
  scheduling/logistics coordination actor (itonami actor pattern,
  ADR-2607121000 / CLAUDE.md Actors section; README's 'Robotics
  premise' — a mine/quarry-site scheduling/logistics coordination
  robot proposes crew/shift scheduling, extraction-log/progress
  logging, safety-concern flagging and mining-equipment/consumables
  order coordination under this advisor/governor pair, which never
  dispatches hardware itself, never performs mining/quarrying work,
  and never finalizes a mine/quarry-operation-execution decision or
  overrides a mine-safety-officer's/site-supervisor's judgment).
  Modeled on cloud-itonami-isco-7232's aerocoord.store.

  Domain:

    site  — a registered mine or quarry site under operation
            (:site-id :name :area).
    miner — a registered miner/quarrier crew member
            {:miner-id :site-id :name :role}, belonging to exactly
            one registered site (the mine/quarry site currently
            assigned to this miner for this operating engagement).
    record — a committed operating record (a logged extraction/
            progress record, scheduling proposal, safety-concern flag
            or supply-order coordination entry) — written ONLY via
            commit-record!. This actor coordinates mine/quarry-site
            scheduling/logistics ONLY — a `record` is a coordination
            artifact, never a mining/quarrying-execution act or a
            mine-safety-officer's-/site-supervisor's-judgment
            override.
    ledger — append-only audit trail, commit or hold.")

(defprotocol Store
  (site [s site-id])
  (miner [s miner-id])
  (records-of [s site-id])
  (ledger [s])
  (register-site! [s st])
  (register-miner! [s m])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (site [_ site-id] (get-in @a [:sites site-id]))
  (miner [_ miner-id] (get-in @a [:miners miner-id]))
  (records-of [_ site-id] (filter #(= site-id (:site-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-site! [s st]
    (swap! a assoc-in [:sites (:site-id st)] st) s)
  (register-miner! [s m]
    (swap! a assoc-in [:miners (:miner-id m)] m) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:sites {} :miners {} :records [] :ledger []}
                                   seed)))))
