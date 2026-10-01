# Experiment and CNCF AI Audit Correlation

## Principle

Textus Experiment remains implementation-neutral. An Experiment records definitions, arms, runs, observations, reservations and derived summaries regardless of whether an arm is implemented by AI, deterministic code, Workflow, an external service or another mechanism.

When an Experiment arm executes AI, the AI implementation follows its ordinary execution path. It does not become Experiment-aware merely because the call belongs to a run.

## Context propagation

Experiment identity is carried by CNCF ExecutionContext/correlation context across the assembled execution path. The intended correlation is:

- experimentId
- experimentRunId
- experimentArmId

or their canonical CNCF equivalents.

An AI runtime API does not require these as Experiment-specific request parameters. CNCF AI Audit captures the inherited correlation automatically when it creates the AI Interaction record.

## Dual records for AI-backed arms

An AI-backed arm produces two records with different authority:

1. Textus Experiment Observation records the comparison meaning: experiment/run/arm, measurement/result/evidence references and acceptance facts.
2. CNCF AI Audit records the AI execution meaning: AI Interaction context, request, response, execution metadata and later evaluation/outcome evidence.

The Experiment Observation may retain an opaque AIInteractionId/evidenceRef. It does not copy raw prompt, context, request, response or provider payloads from AI Audit.

Conversely, AI Audit records inherited Experiment correlation so a detailed AI Interaction can be traced back to the Experiment run/arm.

## Non-AI arms

A deterministic or otherwise non-AI arm does not create an AI Audit record merely because it belongs to an Experiment. Experiment semantics therefore remain independent of AI.

## Consequence

Experiment can compare Prompt A/B, Model/Provider strategies, different AI execution plans, Workflow versions, or AI versus deterministic implementations without introducing Experiment-specific branching into textus-ai-runtime.

Additional safe AI facts may be exposed for measurement when generally useful, but they belong to the ordinary AI Audit/AI observation contract rather than a special Experiment execution API.
