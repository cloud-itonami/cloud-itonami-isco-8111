# cloud-itonami-isco-8111

Open Occupation Blueprint for **ISCO-08 8111**: Miners and Quarriers.

This repository designs a forkable OSS business for a mine/quarry-site scheduling/logistics coordination service: a mine/quarry-site scheduling/logistics coordination robot manages extraction-log/progress record logging, crew/shift scheduling, safety-concern flagging and mining-equipment/consumables order coordination under a governor-gated actor, so the mining/quarrying operator keeps its own operating records instead of renting a closed mine-scheduling SaaS.

**This actor coordinates MINE/QUARRY-SITE SCHEDULING/LOGISTICS ONLY — it never performs mining/quarrying work itself and never makes a mine/quarry-operation-authorization decision.** Miners and quarriers work underground or in open-pit quarry sites — cave-in/collapse, gas exposure, heavy-equipment and fall hazards can cause death or serious injury, categorically higher-stakes than ordinary workshop trades, on par with aviation and blasting in this catalog. The actor's closed op-allowlist contains no op that directly finalizes a mine/quarry-operation-execution decision (authorizing extraction/excavation to proceed), nor overrides a mine-safety-officer's/site-supervisor's judgment. Any proposal that attempts either of these is a hard, permanent block, never overridable by human approval, and NEVER auto-commit-eligible under any confidence level.

**Maturity: `:implemented`.** `src/minecoord/` implements the
`MineCoordActor` as a `langgraph.graph/state-graph`
(`minecoord.actor`) wired to a `Mine/Quarry-Site Scheduling &
Logistics Coordination Advisor` (`minecoord.advisor`) and an
independent `MineCoordGovernor` (`minecoord.governor`), following
the itonami actor pattern (ADR-2607121000): `:intake -> :advise -> :govern -> :decide -+-> :commit
(:ok? true) +-> :request-approval (:escalate? true, human-in-the-loop
interrupt) +-> :hold (:hard? true)`. See `clojure -M:test` output for
the current test/assertion counts.

HARD invariants (always `:hold`, never overridable): the mine/quarry
site record must be independently verified/registered before any
action; a referenced miner must be a registered crew member
belonging to that site; `:effect` must be `:propose` only (no
hardware dispatch, no mining/quarrying work performed); the closed
op-allowlist is enforced (no op in the allowlist finalizes a
mine/quarry-operation-execution decision or overrides mine-safety-
officer/site-supervisor authority); and any proposal that attempts to
directly finalize a mine/quarry-operation-execution decision
(authorizing extraction/excavation to proceed), or override a
mine-safety-officer's/site-supervisor's judgment, is a hard,
**permanent** block — detected as finalization/execution action
phrases (never bare nouns like "mine"/"quarry"/"extraction"/
"excavation", which are ordinary vocabulary for this domain and must
not false-trip the guard).

Always-escalate ops (human sign-off regardless of confidence, mapping
this repo's Trust Controls in
[`docs/business-model.md`](docs/business-model.md)):
`:flag-safety-concern` (every surfaced ground-stability/gas-
detection/equipment-condition concern, ALWAYS, no exceptions, ever)
and `:coordinate-supply-order` above the registered cost threshold.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here a mine/quarry-site scheduling/logistics coordination robot performs extraction-log/progress logging, crew/shift-schedule proposals, safety-concern surfacing and mining-equipment/consumables order coordination under an actor that proposes
actions and an independent **Mine/Quarry-Site Coordination Governor** that gates them. The governor never
dispatches hardware itself, never performs mining/quarrying work, never finalizes a mine/quarry-operation-execution decision, and never overrides a mine-safety-officer's/site-supervisor's judgment; `:high`/`:safety-critical` actions (such as a safety-concern flag or an above-threshold supply order) require human sign-off.

## Core Contract

```text
site roster + miner roster + shift schedule
        |
        v
Mine/Quarry-Site Scheduling & Logistics Coordination Advisor -> MineCoordGovernor -> log record/schedule/order, or human sign-off
        |
        v
robot actions (gated) + operating records + audit ledger
```

No automated advice can dispatch a robot action the governor refuses,
finalize a mine/quarry-operation-execution decision (authorize
extraction/excavation to proceed), override a mine-safety-officer's/
site-supervisor's judgment, suppress an operating record, or disclose
sensitive data without governor approval and audit evidence.

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISCO-08 `8111`). Required capabilities:

- :robotics
- :identity
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
