(ns minecoord.advisor
  "Mine/Quarry-Site Scheduling & Logistics Coordination Advisor — the
  advisor named in this repository's README, proposing a mine/quarry-
  site scheduling/logistics coordination operation (log a work
  record, schedule a crew/shift operation, flag a safety concern,
  coordinate a supply order) from a site roster, miner roster and
  shift schedule. Swappable mock/llm; the advisor ONLY proposes —
  `minecoord.governor` checks site/miner registration, the closed
  op-allowlist and scope-exclusion independently, and always
  escalates safety-concern flags, above-threshold supply orders and
  low-confidence proposals. This actor coordinates MINE/QUARRY-SITE
  SCHEDULING/LOGISTICS ONLY — it never performs mining/quarrying work
  and never proposes to finalize a mine/quarry-operation-execution
  decision or override a mine-safety-officer's/site-supervisor's
  judgment. Modeled on cloud-itonami-isco-7232's aerocoord.advisor.

  A proposal: {:op :log-work-record|:schedule-crew-operation|
               :flag-safety-concern|:coordinate-supply-order
               :effect :propose :site-id str :miner-id str? :cost
               number? :stake kw :confidence n :rationale str}"
  (:require [clojure.edn :as edn]))

(defprotocol Advisor
  (-advise [advisor store request] "request -> proposal map"))

(defn- infer [_store {:keys [op stake site-id miner-id cost task materials
                             concern-type severity description time-window
                             progress-notes equipment-report]
                      :as request}]
  (cond-> {:op op
           :effect :propose
           :site-id site-id
           :stake (or stake :low)
           :confidence (case (or stake :low) :high 0.7 :medium 0.85 :low 0.95)
           :rationale (str "proposed " (name op) " for site " site-id)}
    miner-id (assoc :miner-id miner-id)
    (some? cost) (assoc :cost cost)
    task (assoc :task task)
    materials (assoc :materials materials)
    concern-type (assoc :concern-type concern-type)
    severity (assoc :severity severity)
    description (assoc :description description)
    time-window (assoc :time-window time-window)
    progress-notes (assoc :progress-notes progress-notes)
    equipment-report (assoc :equipment-report equipment-report)))

(defn mock-advisor []
  (reify Advisor
    (-advise [_ store request] (infer store request))))

(def ^:private system-prompt
  "You are a mine/quarry-site scheduling/logistics coordination
   advisor. Given a request, propose an :op (:log-work-record,
   :schedule-crew-operation, :flag-safety-concern or
   :coordinate-supply-order ONLY — no other op exists), the
   :site-id, an honest :confidence and a :stake. You coordinate
   mine/quarry-site scheduling and logistics ONLY: never propose to
   finalize a mine/quarry-operation-execution decision (authorizing
   extraction/excavation to proceed), never propose to override a
   mine-safety-officer's or site-supervisor's judgment, and never
   propose an op outside the closed allowlist above. The governor
   checks site/miner registration and scope independently.
   Safety-concern flags and above-threshold supply orders always
   require human sign-off regardless of confidence.")

(defn- parse-proposal [content]
  (try
    (let [p (edn/read-string content)]
      (if (map? p)
        (assoc p :effect :propose)
        {:op :unknown :effect :propose :confidence 0.0 :stake :high
         :rationale "unparseable LLM response"}))
    (catch #?(:clj Exception :cljs js/Error) _
      {:op :unknown :effect :propose :confidence 0.0 :stake :high
       :rationale "LLM response parse failure"})))

(defn llm-advisor
  [chat-model model-generate-fn gen-opts]
  (reify Advisor
    (-advise [_ _store request]
      (let [msgs [{:role :system :content system-prompt}
                  {:role :user :content (str "operation request: " (pr-str request))}]
            resp (model-generate-fn chat-model msgs gen-opts)]
        (parse-proposal (:content resp))))))
