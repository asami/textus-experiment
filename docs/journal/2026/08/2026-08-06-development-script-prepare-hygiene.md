# Development Script Prepare Separation Hygiene

Status: Open hygiene

## Finding

The development server has partial separation through a cached runtime
classpath, but freshness is not tracked and `scripts/run-server-debug.sh`
starts top-level SBT. There is no explicit aggregate prepare entry or
digest-backed preparation manifest equivalent to ArtScene.

## Required direction

- Add one bounded prepare entry for required build, CAR, dependency, and
  runtime evidence, routed through the shared serialized SBT runner.
- Keep maintained runtime and debug scripts top-level-SBT-free.
- Reject absent or stale evidence using explicit input and artifact digests.
- Do not repeat preparation for a restart when prepared inputs are unchanged.

## Completion evidence

Serialized preparation succeeds; runtime paths contain no top-level SBT;
current evidence runs; missing or stale evidence fails before runtime without
implicit rebuild work.
