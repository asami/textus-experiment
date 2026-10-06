# Textus AI Evaluation / Replay Benchmark

## Purpose

Textus AI Evaluation is a cross-cutting evaluation model for measuring Thinking Engine capability with actual Textus development and operational tasks.

The key idea is not to require an online A/B test at task execution time. If the input, output, execution context, and later outcome have been recorded, the same case can be replayed at any later time against any Thinking Engine, including newly introduced local LLMs.

This makes actual Textus work itself a reusable benchmark.

## Position in the architecture

textus-experiment is the center of the evaluation concept and owns Experiment, Arm, EvaluationRun, comparison, and aggregation.

Related responsibilities are:

- textus-ai-core / AI Runtime: record AI execution input, output, engine, parameters, execution context, and audit references.
- textus-corpus: manage reusable EvaluationCase definitions and corpus membership.
- sm-workflow: provide real task classes such as Implementation, Review, Planning, and their observable outcomes.
- Judge engine: compare candidate outputs using a stronger Thinking Engine when semantic judgment is required.

This extends the existing AI audit correlation and multi-arm experiment designs rather than creating a separate A/B-test subsystem.

## Core model

### EvaluationCase

EvaluationCase represents a reusable evaluation problem.

Typical information:

- sourceExecution reference
- taskClass
- context reference
- input reference
- original output reference
- outcome reference
- evaluation policy

The large original input/output payloads should not be copied merely for evaluation. EvaluationCase should primarily act as an index over authoritative audit/runtime data.

### EvaluationRun

EvaluationRun represents one execution of one or more EvaluationCases against a selected set of Thinking Engines.

An EvaluationCase is stable enough to be reused repeatedly; EvaluationRun is time-, engine-, and configuration-specific.

This separation allows the same historical cases to be rerun months later against a new local model or provider model.

### Arm

Each engine/configuration combination can be represented as an Experiment Arm. Examples include a local Ollama model, Luna, Sol, or another provider/model with a particular reasoning configuration.

The design is naturally multi-arm. A/B comparison is simply the two-arm case.

## Evaluation layers

Evaluation should combine independent evidence instead of relying only on AI-as-a-judge.

1. Deterministic evaluation
   - build
   - test
   - lint
   - other mechanically verifiable checks

2. Outcome evaluation
   - later Review result
   - human approval/rejection
   - subsequent correction
   - workflow completion result

3. Judge evaluation
   - correctness
   - specification conformance
   - design quality
   - unnecessary complexity
   - defect detection quality
   - comparative ranking

Judge evaluation should use a Thinking Engine sufficiently stronger than the evaluated engines when possible.

## Replay model

A normal production execution may later become an EvaluationCase:

```text
Actual workflow execution
        |
        v
AI Runtime Audit
  input / output / engine / context / parameters
        |
        v
EvaluationCase
        |
        +--> Local Engine A
        +--> Local Engine B
        +--> Luna
        +--> future engine
        |
        v
Deterministic + Outcome + Judge evaluation
```

The original execution does not need to know that it will later be used for an experiment.

## Task-class capability map

Aggregated EvaluationRuns can produce a Task Class x Engine capability map, for example distinguishing simple implementation, Scala implementation, test generation, architecture review, lint remediation, and other work classes.

This data can later support an Engine Router: route work to the least expensive engine whose measured capability is sufficient for that task class and reasoning requirement.

The goal is not "use local LLMs because they are cheap", but "measure their capability continuously and delegate only work they have demonstrated they can perform adequately."

## Initial scope

Keep this as a cross-cutting design concept first. Do not create implementation phases yet.

First stabilize:

- EvaluationCase boundary and references
- EvaluationRun / Arm relationship
- deterministic, outcome, and judge evaluation contracts
- AI Runtime audit requirements
- sm-workflow task/outcome linkage
- aggregation needed for future Engine Router decisions

After the cross-component contract is stable, create implementation phases in the responsible components.
