# Textus Experiment

`textus-experiment` defines reproducible offline comparisons over an immutable
`textus-corpus` revision. It owns experiment definitions, treatment arms, run
records, observations, and derived aggregate reports. This is the shared
evaluation and A/B-test control plane; it is not an online request router.

It deliberately does not own:

- corpus fixture content or expected evidence;
- AI provider, model, endpoint, credential, or runtime-profile configuration;
- application-specific acceptance logic or production traffic assignment.

Textus AI resolves the execution plan referenced by an arm. The application
executes its existing acceptance operation and supplies the resulting evidence
reference. `textus-corpus` supplies the fixed input population.

## Contract

The `ExperimentManagement` surface exposes the semantic operations:

- `define-experiment`
- `define-experiment-arm`
- `activate-experiment`
- `start-experiment`
- `complete-experiment-run`
- `record-observation`
- `list-experiment-runs`
- `list-experiment-observations`
- `summarize-experiment-run`

The CML contract intentionally holds references to execution and acceptance
evidence instead of raw prompts, credentials, or provider payloads. Definitions,
arms, runs, and observations are persisted through the CNCF unit-of-work entity
store. Run summaries are derived from immutable observations, so aggregate
results do not become a second source of truth.

Experiment definitions are drafts until they have at least one arm and are
explicitly activated. Runs can start only from active definitions, observations
can be recorded only while a run is running, and completion moves the run to a
terminal state. Exact command retries are idempotent; attempts to replace an
immutable definition or observation are rejected.

The assembled component depends on `textus-corpus`. Corpus revision and case
references are validated through its semantic operations rather than by
importing Corpus implementation classes.

## Operation Evaluation Development Adapter

`OfflineExperimentEvaluationSinkAdapter` implements CNCF's standard
`ExperimentEvaluationSink` for bounded offline and development verification.
It retains automatic operation start/terminal facts and
application-submitted observations in delivery order, deduplicates exact
fact-ID retries, rejects replacement content for an existing fact ID, and
reports capacity saturation through the standard delivery limitation.

The primary component exposes this adapter through the standard SPI provider
contract. Its provider accepts only an absent or explicit `offline` mode. The
adapter is deliberately in-memory and non-persistent. Mapping captured
observations into the component's persistent `record-observation` lifecycle,
measurement/evidence conventions, retention, and production provider
activation remain separately owned follow-up work.

## Development

- artifact: `textus-experiment`
- package: `org.simplemodeling.textus.experiment`
- version: `0.1.0-SNAPSHOT`

Run `sbt cozyGenerate compile` to regenerate and compile the CAR. Generated
Scala sources are under `target/scala-3.3.8/src_managed/main/scala`.
