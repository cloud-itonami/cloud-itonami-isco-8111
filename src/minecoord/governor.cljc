(ns minecoord.governor
  "MineCoordGovernor — the independent safety/traceability layer
  named in this repository's README/business-model.md, gating every
  mine/quarry-site scheduling/logistics coordination proposal an
  advisor may make for a mine or quarry site under operation. The
  governor never dispatches hardware itself, never performs mining/
  quarrying work, and never allows a proposal to finalize a mine/
  quarry-operation-execution decision (authorizing extraction/
  excavation to proceed) or override a mine-safety-officer's/site-
  supervisor's judgment — this actor coordinates MINE/QUARRY-SITE
  SCHEDULING/LOGISTICS ONLY. Modeled on cloud-itonami-isco-7232's
  aerocoord.governor.

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. site provenance         — the mine/quarry site record must be
                                  independently verified/registered
                                  before any action.
    2. no-actuation             — proposal :effect must be :propose
                                  (the governor never dispatches
                                  hardware and never performs mining/
                                  quarrying work; it only gates what
                                  the advisor may coordinate).
    3. closed op-allowlist      — :op must be one of the four
                                  coordination ops (:log-work-record,
                                  :schedule-crew-operation,
                                  :flag-safety-concern,
                                  :coordinate-supply-order). No op
                                  that directly finalizes a mine/
                                  quarry-operation-execution decision
                                  or overrides mine-safety-officer/
                                  site-supervisor authority exists in
                                  this allowlist — these decision
                                  classes are structurally absent, not
                                  merely gated.
    4. site-mismatch            — if the proposal names a site, it
                                  must be the SAME site verified for
                                  this request (defense-in-depth
                                  against a proposal quietly targeting
                                  a different, unverified site).
    5. miner basis               — if the proposal references a
                                  miner, that miner must be a
                                  REGISTERED miner/quarrier crew
                                  member belonging to this site (an
                                  unregistered or foreign-site miner
                                  reference is not a routine
                                  scheduling proposal).
    6. scope-exclusion          — a proposal that attempts to
                                  finalize a mine/quarry-operation-
                                  execution decision (to authorize
                                  extraction/excavation to proceed),
                                  or to override a mine-safety-
                                  officer's or site-supervisor's
                                  judgment, is a hard, PERMANENT block
                                  — never overridable by human
                                  approval, regardless of confidence
                                  or stake, and NEVER auto-commit-
                                  eligible under any confidence level.
                                  Detected as finalization/execution
                                  ACTION PHRASES (e.g. 'authorize the
                                  extraction to proceed', 'finalize
                                  the excavation operation', 'override
                                  the mine safety officer's judgment')
                                  in free-text proposal fields, never
                                  as bare domain nouns ('mine',
                                  'quarry', 'extraction', 'excavation',
                                  'site') — bare-noun matching would
                                  false-trip on the default mock
                                  advisor's own routine rationale
                                  text, since this actor's entire
                                  domain is mine/quarry-site
                                  coordination. See
                                  `minecoord.governor-test`
                                  `default-mock-advisor-proposals-never-self-trip-scope-exclusion`.

  ESCALATION invariants (:escalate? true, ALWAYS human sign-off,
  regardless of confidence):
    7. :op :flag-safety-concern always escalates (a surfaced ground-
                                  stability/gas-detection/equipment-
                                  condition concern always requires
                                  human review — the governor never
                                  resolves a safety concern itself,
                                  and this is unconditional — no
                                  confidence-level exception).
    8. :op :coordinate-supply-order with :cost above
                                  `supply-order-cost-threshold` always
                                  escalates.
    9. low confidence (< `confidence-floor`)."
  (:require [clojure.string :as str]
            [minecoord.store :as store]))

(def confidence-floor 0.6)

(def supply-order-cost-threshold 20000)

(def ^:private allowed-ops
  #{:log-work-record :schedule-crew-operation :flag-safety-concern
    :coordinate-supply-order})

(def ^:private always-escalate-ops #{:flag-safety-concern})

;; Scope-exclusion is matched as finalization/execution ACTION
;; PHRASES, never as bare nouns ("mine", "quarry", "extraction",
;; "excavation", "site") — this actor's entire domain is mine/quarry-
;; site coordination, so bare-noun matching would false-trip on the
;; default mock advisor's own routine rationale text (e.g. "proposed
;; :coordinate-supply-order for site S-1" naming mining equipment, or
;; a crew-schedule proposal naming an extraction face under
;; operation). See governor-test's dedicated self-trip guard.
(def ^:private scope-exclusion-phrases
  ["authorize the extraction to proceed"
   "authorize extraction to proceed"
   "authorize the excavation to proceed"
   "authorize excavation to proceed"
   "authorize the mine operation to proceed"
   "authorize the quarry operation to proceed"
   "authorize the quarrying operation to proceed"
   "authorize the mining operation to proceed"
   "finalize the extraction operation"
   "finalize the excavation operation"
   "finalize the mine operation authorization"
   "finalize the quarry operation authorization"
   "finalize the mining operation execution decision"
   "finalize the quarrying operation execution decision"
   "finalize the mine-operation-execution decision"
   "complete the extraction directly"
   "perform the extraction directly"
   "perform the excavation directly"
   "execute the extraction directly"
   "execute the excavation directly"
   "execute the mining operation directly"
   "execute the quarrying operation directly"
   "dispatch the crew to proceed with extraction"
   "dispatch the crew to begin extraction"
   "sign off the extraction authorization"
   "sign the extraction authorization"
   "issue the extraction authorization"
   "override the mine safety officer's judgment"
   "override the mine safety officer"
   "override the site supervisor's judgment"
   "override the site supervisor"
   "override the quarry safety officer's judgment"
   "override the quarry safety officer"
   "bypass the mine safety officer"
   "bypass the site supervisor"
   "bypass the ground-stability inspection"
   "bypass the gas-detection check"
   "proceed with the excavation without clearance"
   "proceed with the extraction without clearance"])

(defn- scope-excluded-text [proposal]
  (str/lower-case (str (:rationale proposal) " " (:description proposal))))

(defn scope-exclusion-violation?
  "true if any free-text field of `proposal` contains a
  finalization/execution action phrase attempting to finalize a
  mine/quarry-operation-execution decision (authorizing extraction/
  excavation to proceed) or override mine-safety-officer/site-
  supervisor authority. Phrased as multi-word action phrases (never
  bare nouns) so this never false-trips on legitimate mine/quarry-
  site domain vocabulary."
  [proposal]
  (let [text (scope-excluded-text proposal)]
    (boolean (some #(str/includes? text %) scope-exclusion-phrases))))

(defn- hard-violations [{:keys [request proposal]} site-record m]
  (let [{:keys [op site-id miner-id]} proposal]
    (cond-> []
      (nil? site-record)
      (conj {:rule :no-site :detail "未登録 mine/quarry site record"})

      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation :detail "effect は :propose のみ許可（governor は採掘/採石作業判断を直接実行しない）"})

      (not (contains? allowed-ops op))
      (conj {:rule :unknown-op :detail "closed op-allowlist 外の op（採掘/採石 operation 実行の許可決定の確定・mine safety officer/site supervisor の判断の上書きにあたる op は許可されていない）"})

      (and site-id (not= site-id (:site-id request)))
      (conj {:rule :site-mismatch :detail "proposal の site が request で検証済みの site と一致しない"})

      (and miner-id (nil? m))
      (conj {:rule :unknown-miner :detail "未登録 miner への提案は不可"})

      (and m (not= (:site-id m) (:site-id request)))
      (conj {:rule :miner-wrong-site :detail "miner が別 site 所属"})

      (scope-exclusion-violation? proposal)
      (conj {:rule :scope-exclusion-violation
             :detail "採掘/採石 operation 実行の許可決定の確定・mine safety officer/site supervisor の判断の上書きにあたる提案は恒久的に禁止（human 承認でも上書き不可）"}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `minecoord.store/Store`. Pure — never mutates
  the store, never dispatches a robot action, never performs mining/
  quarrying work."
  [request context proposal store]
  (let [site-record (store/site store (:site-id request))
        m (some->> (:miner-id proposal) (store/miner store))
        hard (hard-violations {:request request :proposal proposal} site-record m)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        cost (:cost proposal)
        over-threshold? (and (= :coordinate-supply-order (:op proposal))
                              (number? cost) (> cost supply-order-cost-threshold))
        always-risky? (or (contains? always-escalate-ops (:op proposal)) over-threshold?)]
    {:ok? (and (not hard?) (not low?) (not always-risky?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? always-risky?))}))
