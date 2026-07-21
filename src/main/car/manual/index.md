# Textus Experiment Reference Manual

Textus Experiment defines reproducible offline comparisons over one immutable
Textus Corpus revision. It owns experiment definitions, arms, runs,
observations, and derived summaries; it does not route production traffic.

## Service

`ExperimentManagement` provides operations to define and activate an
experiment, define arms, start and complete runs, record observations, list run
evidence, and summarize a completed population.

Arms refer to Textus AI execution plans. Observations refer to application
acceptance evidence rather than embedding prompts, credentials, or raw provider
payloads.

See [User Guide](user-guide.md) for the lifecycle.
