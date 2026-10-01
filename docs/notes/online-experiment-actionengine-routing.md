# Online Experiment and CNCF ActionEngine Routing

## Direction

Textus Experiment is extended conceptually from reproducible offline comparison to also support online Experiment assignment while remaining the Experiment authority. CNCF Phase 98 owns execution-time integration in ActionEngine.

Textus Experiment owns:
- Experiment and Arm definitions;
- online assignment policy semantics;
- Arm execution-plan references;
- Experiment Run lifecycle;
- Observations and summaries.

CNCF owns:
- detecting an admitted online Experiment Binding at the operation execution chokepoint;
- obtaining/resolving assignment through the Experiment-facing contract;
- propagating Experiment/Run/Arm correlation in ExecutionContext;
- resolving an admitted Arm execution plan to effective parameters/configuration or a compatible physical Operation;
- normal ActionEngine/Provider execution, authorization, observability and Operation Evaluation delivery.

## Online execution

~~~text
Production Request
  -> Logical Operation
  -> CNCF ActionEngine
  -> Experiment Binding
  -> Experiment assignment
  -> Arm
  -> Execution Plan
  -> physical execution
  -> Observation
~~~

The Operation implementation is not Experiment-aware.

## Offline and online

Offline:
- immutable Corpus revision/cases provide the evaluation population;
- an evaluation driver explicitly exercises Arms.

Online:
- real application requests provide the population;
- assignment policy selects an Arm before execution;
- Corpus is not required for each request.

Both modes use Experiment/Arm/Run/Observation/Summary concepts where applicable.

## Execution-plan flexibility

An Arm may represent:
- the same Operation with different effective parameters/configuration;
- a different compatible Operation;
- deterministic versus AI-backed execution.

AI-backed execution independently produces CNCF AI Audit evidence and inherits Experiment correlation from ExecutionContext.

## Display/UI experiment integration

The same online assignment can span presentation and backend execution. CNCF assigns the subject to an Arm before Display Model delivery. Display Model carries a client-safe assigned Arm/variant and a Display Instance/correlation reference. UI runtimes such as TFAF use the value only to choose the assigned presentation; they do not perform assignment.

Display interactions and Display Mutations return/preserve the Display Instance/correlation reference. CNCF restores the authoritative Experiment/Run/Arm and records the resulting mutation and later Operation outcomes against that Arm.

This allows Experiment analysis to correlate which UI variant was actually shown with edit/save/delete/validation/cancel behavior, Business Operations, AI-backed calculations and final business outcomes. If AI is invoked later, AI Audit supplies detailed evidence through the same Experiment correlation.

An Experiment Arm can therefore describe an experience/execution plan containing presentation variant, backend Operation plan and optional AI strategy, rather than being limited to one backend parameter.

## Multi-arm first model

The primary online model is Multi-Arm Experiment, not binary A/B testing. An Experiment owns N Arms; A/B testing is the N=2 specialization.

Assignment policy is decomposed into Allocation Policy and Stickiness Policy. Allocation determines the distribution among eligible Arms; Stickiness determines whether a request/session/user/device/entity remains on an assigned Arm. This separation allows fixed/uniform/weighted allocation initially and later adaptive allocation such as Thompson Sampling or UCB without changing CNCF ActionEngine or Arm execution semantics.

Observation/Measurement and Reward Policy are separate from allocation. Adaptive policies may update their allocation state from admitted reward evidence. Reward is application-defined and can represent downstream business success, validation/admission outcome, human correction, latency/cost-bounded success, or another meaningful outcome rather than only clicks.

An Arm remains an execution/experience plan. It does not know whether assignment came from fixed A/B allocation, a weighted multi-arm policy or an adaptive bandit.
