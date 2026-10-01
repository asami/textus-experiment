# Multi-arm online experimentation direction

Date: 2026-10-01

Decision: Textus Experiment treats Multi-Arm Experiment as the primary model. Binary A/B testing is the two-arm specialization.

Online assignment separates Allocation Policy from Stickiness Policy. The initial contract should support fixed/uniform/weighted allocation while allowing adaptive implementations such as Thompson Sampling and UCB to be added inside textus-experiment without ActionEngine changes.

Observation/Measurement and Reward Policy are separate from assignment. Adaptive allocation may consume admitted reward evidence, including downstream business outcomes correlated through Display Mutation, Operations and AI Audit. CNCF does not define the business reward.

Arm remains an execution/experience plan and is independent of the allocation algorithm.
