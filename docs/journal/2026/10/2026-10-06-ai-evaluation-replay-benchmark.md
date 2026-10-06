# 2026-10-06 AI Evaluation / Replay Benchmark

A local-LLM A/B-test idea was generalized into a cross-cutting AI Evaluation design.

The initial idea was to send the same Implementation or Review problem to multiple Thinking Engines and let a stronger engine evaluate the results. The important extension is that this does not need to happen at the original execution time. When AI Runtime has retained the input, output, engine/configuration, execution context, and later outcome, the case can be replayed at any time against any current or future Thinking Engine.

This turns real Textus work into a reusable benchmark corpus.

The design center is textus-experiment. Existing Experiment/Arm, AI audit correlation, and multi-arm concepts are extended with EvaluationCase and EvaluationRun rather than creating a separate local-LLM A/B subsystem.

Evaluation is intentionally three-layered:

- deterministic evidence such as build/test/lint,
- operational outcome such as Review, human approval, correction, and workflow result,
- semantic Judge evaluation by a stronger Thinking Engine.

textus-corpus indexes reusable EvaluationCases; textus-ai-core / AI Runtime supplies authoritative execution/audit data; sm-workflow supplies real task classes and outcomes.

A long-term consequence is a Task Class x Engine capability map that can feed the future Engine Router. Local engines can then receive work according to measured capability rather than model reputation or cost alone.

For now this is recorded as a cross-component design. Do not create a phase until the contracts among EvaluationCase, EvaluationRun, audit references, outcomes, and evaluation policies are sufficiently stable.

See: `docs/notes/ai-evaluation-replay-benchmark.md`.
