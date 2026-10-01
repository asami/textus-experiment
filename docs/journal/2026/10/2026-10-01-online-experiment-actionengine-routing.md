# Online Experiment routing direction

Date: 2026-10-01

Decision: online A/B testing will integrate with CNCF ActionEngine rather than require Experiment-aware Operation implementations.

Textus Experiment remains authoritative for Experiment/Arm/Run, assignment policy, execution-plan references, observations and summaries. CNCF Phase 98 resolves an applicable online Experiment Binding before physical execution, selects an Arm through the admitted Experiment contract, propagates correlation through ExecutionContext, and executes the resolved plan through the normal ActionEngine/Provider path.

This supports both same-operation parameter/configuration variants and routing to different compatible Operations. Offline Corpus-driven experiments remain supported and distinct. Online requests do not require Corpus cases.

If an Arm invokes AI, CNCF AI Audit records the AI Interaction automatically using the inherited Experiment correlation; Textus AI remains unaware of Experiment semantics.
