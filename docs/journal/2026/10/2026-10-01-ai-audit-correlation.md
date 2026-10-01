# AI Audit correlation for Experiment execution

Date: 2026-10-01

Decision: Textus Experiment remains AI-independent. Experiment/run/arm identity propagates through CNCF ExecutionContext. If an arm ultimately invokes AI, CNCF AI Audit records the normal AI Interaction and automatically captures that inherited Experiment correlation.

The Experiment Observation and AI Audit Interaction are both retained because they answer different questions. Experiment is authoritative for the comparison run and its observation; AI Audit is authoritative for detailed AI execution evidence. An AI-backed Observation may carry AIInteractionId/evidenceRef for drill-down, while detailed prompt/context/request/response data remains in AI Audit.

No Experiment-specific parameters or branching are required in textus-ai-runtime. A non-AI arm creates no AI Audit record solely due to Experiment participation.

This supports A/B testing of prompts and AI strategies as well as AI-versus-deterministic implementations while preserving the generic Experiment model.
