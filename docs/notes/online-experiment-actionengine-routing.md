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
