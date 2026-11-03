# Changelog

## 2026-11-03 — Android `0.14.3` and repository refresh

### Added

- Android local MNN text mode with model ZIP import, per-character cloud/local routing,
  repeat protection and rolling local summaries.
- In-app Local Dream SD1.5/QNN avatar generation plus Qwen-Image cloud generation.
- Local Dream positive/negative prompts, sampler, steps, CFG, seed and gender-aware
  appearance prompt generation.
- Android local ASR and multi-voice Kokoro TTS integration.
- Current production tests, fixed evaluation fixtures and selected implementation docs.

### Changed

- Updated Android source to `0.14.3 / versionCode 27`; no new prebuilt APK is
  published while the mobile client is still changing.
- Synchronized the public PC and Android source trees with the current application.
- Rewrote the README around the actual PC/Android feature split, reproduction steps,
  limitations and data boundaries.
- Renamed the repository concept to `shiyu-ai-companion-chat` because the project is an
  API/local-model role companion rather than only a Qwen demo.

### Verification

- Public PC Python regression: 285 passed.
- API-only clean product suite: 208 passed, 1 skipped.
- Frontend state tests: 40 passed; React production build passed.
- Android JVM: 60 passed; `assembleDebug` passed.
- Android source build passed locally; the public repository intentionally contains no new APK.

## 2026-10-31 — `0.12.0` memory governance and clean reproduction

### Added

- PC structured-memory administration for manual correction, incorrect marking, history restoration, topic suppression and suppression removal, with version chains, audit events and optimistic concurrency checks.
- Private redacted natural-conversation memory suite with 18 cases, 448 turns and 90 probes. The final golden replay passes 90/90 for facts-only, summary-plus-facts, hybrid retrieval and hybrid-state retrieval; recent-only remains 23/90.
- A bounded live natural-memory pipeline run using one synthetic case. Extraction, semantic review, storage, historical retrieval and final answers reached 3/3 probe checks after preserving failed runs and fixing current/history splitting, key canonicalization and duplicate history handling.
- Anonymous enabled-vs-disabled internal-state review with 12 fixed real API pairs, fresh isolated OpenCode sessions and a bounded retry policy.
- The author completed the blinded 12-pair review: state enabled won 6, state disabled won 2 and 4 tied. Naturalness was 5–2 and both emotional continuity and relationship consistency were 3–0/2–0 for state enabled, so normal PC chat keeps the state prompt enabled.
- Clean Windows verification for dependency health, tests, frontend build, isolated SQLite, encrypted Key persistence, role creation, first chat and restart recovery.

### Changed

- Removed built-in characters from the PC product catalogue and Android new-install seed. Android upgrades purge retired roles and their messages, memories, traces, states, jobs and assets. The current PC product database has zero retained data for retired roles.
- Broad memory recall, historical wording, pets, education, drinks, items and anaphoric references now map to stable fact concepts.
- Corrected same-message facts such as “now杭州 / formerly苏州” into one canonical current value and one historical value instead of two conflicting items.
- The Prompt inspector now reports the selected cloud Provider and its 16K input budget; cloud requests expose a clearly labelled local token estimate because exact tokenization happens server-side.
- The secret-runtime installer now accepts any Python path and no longer contains a developer-machine absolute path.
- Product version is PC and Android `0.12.0`; Android is `versionCode 16` and keeps Room schema 11. GitHub was intentionally not synchronized this round.

### Verification

- PC Python: 426 passed.
- Frontend state tests: 40 passed; React production build passed.
- Independent API environment: 208 passed, 1 skipped.
- Android JVM: 43 passed; `assembleDebug` passed.
- Current-host Windows verification passed using an isolated temporary database and deterministic Provider; a real OpenCode first chat also passed in a separate isolated service.
- The current-host checks do not replace a genuine new-machine installation.

### Pending

- A larger frozen live natural-memory evaluation beyond one synthetic case.
- Direct compatibility routes for retired built-in test IDs, if they need to be blocked at the HTTP layer.
- Android `0.12.0` on-device upgrade; no ADB device was connected during this run.
- GitHub synchronization remains paused by author decision.

## 2026-10-30 — PC `0.11.2` disable behavior rules by default

- Completed a 12-pair blind review on the complete-character fixture Shen Zhiyao.
- All cases: baseline won 5, structured rules won 1, and 6 tied.
- Among the seven cases where a rule was actually injected: baseline won 4, structured rules won 1, and 2 tied.
- Naturalness in the rule-applied subset: baseline 5, structured rules 1, tie 1.
- The only rule win was the forced-romance boundary; the baseline was preferred for identity, role-claim isolation, uncertainty and privacy.
- Dynamic behavior rules are now disabled by default in the product prompt; the rules, Trace metadata, evaluation runner and blind-review tools remain available as explicit experiments.
- `PromptPipeline` behavior-rule enablement is opt-in and disabled in normal chat.

## 2026-10-30 — PC `0.11.1` blind-review correction

### Author review result

- Eleven anonymous paired cases were reviewed: structured rules won 2, baseline won 3, and 6 tied.
- Naturalness favored the no-rule condition 7:1 with 3 ties.
- The review exposed over-injection: repeating naturalness and emotional rules constrained already-good answers.
- D09 also showed a product tradeoff: the reviewer preferred a plausible fabricated hometown over an out-of-character “档案里没写” refusal.

### Changed

- Keep 12 codified rules, but dynamically inject only 8 boundary guards; BR06/BR08/BR09/BR10 remain base-prompt contracts.
- Reduce the dynamic prompt cap from six to four rules.
- Stop injecting privacy or emotional rules into ordinary support and explicit-memory-reminder turns that the base prompt already handles.
- Rewrite identity, historical knowledge, privacy, relationship and uncertainty rules to stay in character without mentioning internal fields.
- Trace distinguishes injected guardrails from base-contract rules.

The prior 11/11 automatic result belongs to rule set `2026-10-30.2`. Rule set `2026-10-30.3` must receive a new live evaluation before making a new quality claim.

### Complete-character evaluation correction

- Added a dedicated, complete fictional evaluation card for Shen Zhiyao; built-in Xiaoman and Yanchuan are no longer the primary quality fixtures.
- Added 12 frozen scenarios covering confirmed identity facts, current corrections, role claims, uncertainty, privacy, historical knowledge, relationship boundaries and natural interaction.
- Added a fixed seed, bounded retries, invisible-output rejection, and attribution only when a rule was actually injected.
- Corrected obvious semantic-score alternatives after retaining the first failed run.
- Result: baseline 11/12 and rules 11/12 overall; five cases did not inject a rule; among seven attributable cases, baseline was 7/7 and rules 6/7 under automatic lexical checks.
- No automatic rule benefit was established. The only automatic regression is a semantically correct relationship refusal that missed the old keyword list.
- A new 12-pair anonymous review packet was generated for Shen Zhiyao.

## 2026-10-30 — PC structured character behavior rules

### Added

- Twelve structured, scenario-selected behavior rules for identity limits, historical knowledge boundaries, corrections, subject isolation, privacy, emotional support, relationship consent, natural reply rhythm, character agency, uncertainty, and conflict repair.
- Deterministic rule selection with at most six rules per turn.
- Behavior-rule version, hash, selected IDs, priorities, and trigger terms in PC Trace metadata.
- Separate behavior-rule evaluation that changes only the rule section while keeping the role card, history, memory, model, and generation parameters fixed.

### Results

- Fixed real API comparison: 9/11 without the structured rule section, 11/11 with it.
- D09 fabricated hometown changed to an explicit unknown; D10 historical Python boundary changed to an explicit era-aware refusal.
- No automatic-rule regression across the selected 11 scenarios.
- PC Python suite expanded to 396 passing tests.

### Scope

- PC version becomes `0.11.0`.
- Android remains `0.10.0` until the rules are deliberately migrated after PC review.
- The behavior suite is a single-run smoke evaluation; author naturalness review remains pending.
- GitHub synchronization remains paused by author decision.

## 2026-10-30 — API role quality, trace, and memory semantics

### Added

- PC and Android per-turn Trace with model ID, prompt/role versions, prompt hash, selected memory IDs and scores, internal state, token usage, latency and response source.
- PC Trace endpoint and simple developer panel; Android Trace sheet.
- Fixed role-quality dataset with 24 dialogue scenarios and 12 memory-semantic cases.
- Batched semantic verification for automatically extracted memory.
- Memory metadata for fact type, epistemic status, source authority, knowledge source, known time and visibility.
- Role improvisation is stored as `role_claim` and cannot silently overwrite canonical character facts.
- Android Room schema 11 and `0.10.0 / versionCode 15`.

### Results

- Real OpenCode Go evaluation: baseline 18/24, structured-memory condition 22/24.
- Automatic judgments found four memory-context improvements and no regressions.
- Two boundary failures remain and are retained: fabricated hometown and historical-character modern-code reasoning.
- Live semantic memory verification: 12/12 expected decisions.
- Public production Python tests: 236; full local API product tests: 390.
- Frontend state tests: 39; React production build passed.
- Android JVM tests: 43; `assembleDebug` passed.

### Known limits

- The dialogue suite is a single-run smoke comparison, not a statistical benchmark.
- Naturalness still requires independent human review.
- Character behavior rules are intentionally not implemented in this round.
