package org.simplemodeling.textus.experiment

import java.time.{Clock, Instant}

import org.goldenport.Consequence
import org.goldenport.cncf.action.Action
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.context.{DataStoreContext, EntityStoreContext, ExecutionContext, ScopeContext, ScopeKind, SecurityContext}
import org.goldenport.cncf.datastore.{DataStore, DataStoreSpace}
import org.goldenport.cncf.entity.EntityStoreSpace
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.testutil.ExecutionContextTestFixture
import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.protocol.{Property, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.simplemodeling.textus.experiment.impl.{ComponentFactory, ExperimentPrimaryComponent}

/*
 * @since   Jul. 21, 2026
 *  version Jul. 27, 2026
 * @version Aug. 14, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentFactorySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Textus Experiment" should {
    "publish only the semantic experiment service" in {
      Given("a primary Experiment component")
      val component = _component()

      When("its protocol is inspected")
      val services = component.protocol.services.services
      val operations = services.find(_.name == "ExperimentManagement").toVector
        .flatMap(_.operations.operations.toVector).map(_.name)

      Then("generic aggregate, view, and entity mutation services are absent")
      services.map(_.name) should contain ("ExperimentManagement")
      services.map(_.name) should not contain allOf ("aggregate", "view", "entity")
      operations should contain theSameElementsAs Vector(
        "defineExperiment",
        "defineExperimentArm",
        "activateExperiment",
        "startExperiment",
        "reserveComparisonReplay",
        "consumeComparisonReplay",
        "cancelComparisonReplay",
        "expireComparisonReplayReservations",
        "listComparisonReplayReservations",
        "completeExperimentRun",
        "recordObservation",
        "listExperimentRuns",
        "listExperimentObservations",
        "summarizeExperimentRun"
      )
    }

    "run a two-arm comparison and derive its outcome summary" in {
      Given("a draft experiment over one immutable corpus revision")
      val component = _component()
      given ExecutionContext = _with_privilege(component.logic.executionContext(), SecurityContext.Privilege.System)
      val corpusrevisionid = _external_id("textus-corpus", "corpus_revision", "01")
      val corpuscaseone = _external_id("textus-corpus", "corpus_case", "11")
      val corpuscasetwo = _external_id("textus-corpus", "corpus_case", "12")
      val firstdefinition = _record(_operation(component, "defineExperiment",
        "experimentKey" -> "sanpomap-gemma-profile",
        "name" -> "Sanpomap Gemma profile",
        "corpusRevisionId" -> corpusrevisionid,
        "acceptanceOperation" -> "Sanpomap.Sanpomap.evaluateAiResult",
        "description" -> "Compare baseline and Gemma-first execution plans"
      ))
      val seconddefinition = _record(_operation(component, "defineExperiment",
        "experimentKey" -> "sanpomap-gemma-profile",
        "name" -> "Sanpomap Gemma profile",
        "corpusRevisionId" -> corpusrevisionid,
        "acceptanceOperation" -> "Sanpomap.Sanpomap.evaluateAiResult",
        "description" -> "Compare baseline and Gemma-first execution plans"
      ))
      val experimentid = firstdefinition.getString("id").getOrElse(fail("experiment id missing"))
      val baseline = _record(_operation(component, "defineExperimentArm",
        "experimentId" -> experimentid,
        "armKey" -> "baseline",
        "name" -> "Baseline",
        "executionPlanReference" -> "textus-ai-plan://sanpomap/baseline"
      ))
      val candidate = _record(_operation(component, "defineExperimentArm",
        "experimentId" -> experimentid,
        "armKey" -> "gemma-first",
        "name" -> "Gemma first",
        "executionPlanReference" -> "textus-ai-plan://sanpomap/gemma-first"
      ))

      When("the experiment is activated and observations are recorded for one run")
      _record(_operation(component, "activateExperiment", "experimentId" -> experimentid))
        .getString("status") shouldBe Some("Active")
      val run = _record(_operation(component, "startExperiment",
        "experimentId" -> experimentid,
        "runReference" -> "run-20260721-001"
      ))
      val runid = run.getString("id").getOrElse(fail("run id missing"))
      val baselineid = baseline.getString("id").getOrElse(fail("baseline arm id missing"))
      val candidateid = candidate.getString("id").getOrElse(fail("candidate arm id missing"))
      val firstobservation = _record(_operation(component, "recordObservation",
        "experimentRunId" -> runid,
        "experimentArmId" -> baselineid,
        "corpusCaseId" -> corpuscaseone,
        "outcome" -> "accepted",
        "acceptanceEvidenceReference" -> "sanpomap-evidence://run-001/baseline/case-01",
        "executionEvidenceReference" -> "textus-ai-evidence://run-001/baseline/case-01"
      ))
      val retriedobservation = _record(_operation(component, "recordObservation",
        "experimentRunId" -> runid,
        "experimentArmId" -> baselineid,
        "corpusCaseId" -> corpuscaseone,
        "outcome" -> "accepted",
        "acceptanceEvidenceReference" -> "sanpomap-evidence://run-001/baseline/case-01",
        "executionEvidenceReference" -> "textus-ai-evidence://run-001/baseline/case-01"
      ))
      _record(_operation(component, "recordObservation",
        "experimentRunId" -> runid,
        "experimentArmId" -> candidateid,
        "corpusCaseId" -> corpuscaseone,
        "outcome" -> "repaired",
        "acceptanceEvidenceReference" -> "sanpomap-evidence://run-001/gemma/case-01",
        "executionEvidenceReference" -> "textus-ai-evidence://run-001/gemma/case-01"
      ))
      _record(_operation(component, "recordObservation",
        "experimentRunId" -> runid,
        "experimentArmId" -> candidateid,
        "corpusCaseId" -> corpuscasetwo,
        "outcome" -> "escalated",
        "acceptanceEvidenceReference" -> "sanpomap-evidence://run-001/gemma/case-02",
        "executionEvidenceReference" -> "textus-ai-evidence://run-001/gemma/case-02"
      ))
      val summary = _record(_operation(component, "summarizeExperimentRun", "experimentRunId" -> runid))

      Then("definition and observation retries are idempotent and the summary is derived from three facts")
      seconddefinition.getString("id") shouldBe firstdefinition.getString("id")
      retriedobservation.getString("id") shouldBe firstobservation.getString("id")
      summary.getInt("observationCount") shouldBe Some(3)
      summary.getInt("acceptedCount") shouldBe Some(1)
      summary.getInt("repairedCount") shouldBe Some(1)
      summary.getInt("escalatedCount") shouldBe Some(1)
      summary.getInt("rejectedCount") shouldBe Some(0)

      And("the completed run retains its immutable summary and accepts no new observation")
      _record(_operation(component, "completeExperimentRun",
        "experimentRunId" -> runid,
        "status" -> "Completed",
        "completionEvidenceReference" -> "experiment-evidence://run-001/summary"
      )).getString("status") shouldBe Some("Completed")
      val completedsummary = _record(_operation(component, "summarizeExperimentRun", "experimentRunId" -> runid))
      completedsummary.getInt("observationCount") shouldBe Some(3)
      completedsummary.getInt("acceptedCount") shouldBe Some(1)
      completedsummary.getInt("repairedCount") shouldBe Some(1)
      completedsummary.getInt("escalatedCount") shouldBe Some(1)
      val lateobservation = _operation(component, "recordObservation",
        "experimentRunId" -> runid,
        "experimentArmId" -> baselineid,
        "corpusCaseId" -> corpuscasetwo,
        "outcome" -> "accepted"
      )
      _failure_message(lateobservation) should include ("Running")
    }

    "reject immutable definition replacement and draft-only arm changes" in {
      Given("an active experiment")
      val component = _component()
      given ExecutionContext = _with_privilege(component.logic.executionContext(), SecurityContext.Privilege.System)
      val corpusrevisionid = _external_id("textus-corpus", "corpus_revision", "21")
      val definition = _record(_operation(component, "defineExperiment",
        "experimentKey" -> "immutable-experiment",
        "name" -> "Immutable experiment",
        "corpusRevisionId" -> corpusrevisionid,
        "acceptanceOperation" -> "Sanpomap.Sanpomap.evaluateAiResult"
      ))
      val experimentid = definition.getString("id").getOrElse(fail("experiment id missing"))
      _record(_operation(component, "defineExperimentArm",
        "experimentId" -> experimentid,
        "armKey" -> "baseline",
        "name" -> "Baseline",
        "executionPlanReference" -> "textus-ai-plan://sanpomap/baseline"
      ))
      _record(_operation(component, "activateExperiment", "experimentId" -> experimentid))

      When("the definition is replaced and a new arm is added after activation")
      val replacement = _operation(component, "defineExperiment",
        "experimentKey" -> "immutable-experiment",
        "name" -> "Changed experiment",
        "corpusRevisionId" -> corpusrevisionid,
        "acceptanceOperation" -> "Sanpomap.Sanpomap.changedAcceptance"
      )
      val latearm = _operation(component, "defineExperimentArm",
        "experimentId" -> experimentid,
        "armKey" -> "late",
        "name" -> "Late arm",
        "executionPlanReference" -> "textus-ai-plan://sanpomap/late"
      )

      Then("both changes are rejected by lifecycle policy")
      _failure_message(replacement) should include ("different immutable content")
      _failure_message(latearm) should include ("Draft")
    }

    "reject comparison replay reservation without operator scheduler configuration" in {
      Given("a running experiment without comparison replay configuration")
      val component = _component()
      given ExecutionContext = _with_privilege(component.logic.executionContext(), SecurityContext.Privilege.System)
      val experiment = _record(_operation(component, "defineExperiment",
        "experimentKey" -> "unconfigured-comparison-reservation",
        "name" -> "Unconfigured comparison reservation",
        "corpusRevisionId" -> _external_id("textus-corpus", "corpus_revision", "30"),
        "acceptanceOperation" -> "Sanpomap.Evaluation.evaluateAiCandidate"
      ))
      val experimentid = experiment.getString("id").getOrElse(fail("experiment id missing"))
      _record(_operation(component, "defineExperimentArm",
        "experimentId" -> experimentid,
        "armKey" -> "candidate",
        "name" -> "Candidate",
        "executionPlanReference" -> "textus-ai-plan://sanpomap/candidate"
      ))
      _record(_operation(component, "activateExperiment", "experimentId" -> experimentid))
      val run = _record(_operation(component, "startExperiment",
        "experimentId" -> experimentid,
        "runReference" -> "unconfigured-run"
      ))

      When("the application asks for a reservation")
      val failure = _operation(component, "reserveComparisonReplay",
        "experimentRunId" -> run.getString("id").getOrElse(fail("run id missing")),
        "reservationKey" -> "unconfigured",
        "replayCount" -> "1",
        "budgetMicrounits" -> "1"
      )

      Then("the scheduler refuses before a replay route can run")
      _failure_message(failure) should include ("comparison-replay.enabled=true is required")
    }

    "expire a zero-lifetime reservation without executing a replay" in {
      Given("a running experiment whose operator lifetime is explicitly zero")
      val component = _component(
        Clock.systemUTC(),
        "textus.experiment.comparison-replay.enabled" -> "true",
        "textus.experiment.comparison-replay.maximum-replay-count" -> "1",
        "textus.experiment.comparison-replay.maximum-budget-microunits" -> "1",
        "textus.experiment.comparison-replay.reservation-ttl-seconds" -> "0"
      )
      given ExecutionContext = _with_privilege(component.logic.executionContext(), SecurityContext.Privilege.System)
      val experiment = _record(_operation(component, "defineExperiment",
        "experimentKey" -> "immediate-expiry-comparison-reservation",
        "name" -> "Immediate expiry comparison reservation",
        "corpusRevisionId" -> _external_id("textus-corpus", "corpus_revision", "32"),
        "acceptanceOperation" -> "Sanpomap.Evaluation.evaluateAiCandidate"
      ))
      val experimentid = experiment.getString("id").getOrElse(fail("experiment id missing"))
      _record(_operation(component, "defineExperimentArm",
        "experimentId" -> experimentid,
        "armKey" -> "candidate",
        "name" -> "Candidate",
        "executionPlanReference" -> "textus-ai-plan://sanpomap/candidate"
      ))
      _record(_operation(component, "activateExperiment", "experimentId" -> experimentid))
      val run = _record(_operation(component, "startExperiment",
        "experimentId" -> experimentid,
        "runReference" -> "immediate-expiry-run"
      ))
      _record(_operation(component, "reserveComparisonReplay",
        "experimentRunId" -> run.getString("id").getOrElse(fail("run id missing")),
        "reservationKey" -> "immediate-expiry",
        "replayCount" -> "1",
        "budgetMicrounits" -> "1"
      ))

      When("the scheduler expiration operation runs")
      val result = _record(_operation(component, "expireComparisonReplayReservations"))

      Then("the reservation is terminal before a provider route can start")
      result.getInt("expiredCount") shouldBe Some(1)
    }

    "persist bounded comparison replay reservations through their lifecycle" in {
      Given("an active experiment run and an operator-owned replay envelope")
      val now = Instant.now()
      val clock = Clock.fixed(now, java.time.ZoneOffset.UTC)
      val component = _component(
        clock,
        "textus.execution.profile" -> "standard",
        "textus.experiment.comparison-replay.enabled" -> "true",
        "textus.experiment.comparison-replay.maximum-replay-count" -> "2",
        "textus.experiment.comparison-replay.maximum-budget-microunits" -> "100",
        "textus.experiment.comparison-replay.reservation-ttl-seconds" -> "1"
      )
      given ExecutionContext = _with_privilege(ExecutionContext.create(clock), SecurityContext.Privilege.System)
      val experiment = _record(_operation(component, "defineExperiment",
        "experimentKey" -> "comparison-reservation",
        "name" -> "Comparison reservation",
        "corpusRevisionId" -> _external_id("textus-corpus", "corpus_revision", "31"),
        "acceptanceOperation" -> "Sanpomap.Evaluation.evaluateAiCandidate"
      ))
      val experimentid = experiment.getString("id").getOrElse(fail("experiment id missing"))
      _record(_operation(component, "defineExperimentArm",
        "experimentId" -> experimentid,
        "armKey" -> "candidate",
        "name" -> "Candidate",
        "executionPlanReference" -> "textus-ai-plan://sanpomap/candidate"
      ))
      _record(_operation(component, "activateExperiment", "experimentId" -> experimentid))
      val run = _record(_operation(component, "startExperiment",
        "experimentId" -> experimentid,
        "runReference" -> "comparison-run"
      ))
      val runid = run.getString("id").getOrElse(fail("run id missing"))
      val otherrun = _record(_operation(component, "startExperiment",
        "experimentId" -> experimentid,
        "runReference" -> "comparison-other-run"
      ))
      val otherrunid = otherrun.getString("id").getOrElse(fail("other run id missing"))

      When("two bounded reservations are admitted and one is cancelled")
      val first = _record(_operation(component, "reserveComparisonReplay",
        "experimentRunId" -> runid,
        "reservationKey" -> "first",
        "replayCount" -> "1",
        "budgetMicrounits" -> "40"
      ))
      val retry = _record(_operation(component, "reserveComparisonReplay",
        "experimentRunId" -> runid,
        "reservationKey" -> "first",
        "replayCount" -> "1",
        "budgetMicrounits" -> "40"
      ))
      val crossrunreservation = _operation(component, "reserveComparisonReplay",
        "experimentRunId" -> otherrunid,
        "reservationKey" -> "first",
        "replayCount" -> "1",
        "budgetMicrounits" -> "40"
      )
      val second = _record(_operation(component, "reserveComparisonReplay",
        "experimentRunId" -> runid,
        "reservationKey" -> "second",
        "replayCount" -> "1",
        "budgetMicrounits" -> "60"
      ))
      val overcapacity = _operation(component, "reserveComparisonReplay",
        "experimentRunId" -> runid,
        "reservationKey" -> "over-capacity",
        "replayCount" -> "1",
        "budgetMicrounits" -> "1"
      )
      val secondid = second.getString("id").getOrElse(fail("second reservation id missing"))
      _record(_operation(component, "cancelComparisonReplay",
        "reservationId" -> secondid,
        "cancellationEvidenceReference" -> "sanpomap-evidence://comparison/cancelled"
      ))
      val replacement = _record(_operation(component, "reserveComparisonReplay",
        "experimentRunId" -> runid,
        "reservationKey" -> "replacement",
        "replayCount" -> "1",
        "budgetMicrounits" -> "60"
      ))

      Then("idempotence is preserved and only active or consumed capacity blocks admission")
      first.getString("id") shouldBe retry.getString("id")
      _failure_message(crossrunreservation) should include ("different immutable content")
      _failure_message(overcapacity) should include ("per-run operator maximum")
      replacement.getString("status") shouldBe Some("Reserved")

      When("another experiment run attempts to consume the reservation")
      val firstid = first.getString("id").getOrElse(fail("first reservation id missing"))
      val crossrunconsume = _operation(component, "consumeComparisonReplay",
        "experimentRunId" -> otherrunid,
        "reservationId" -> firstid,
        "observationReference" -> "sanpomap-evidence://comparison/cross-run-observation"
      )

      Then("the reservation remains bound to its owning experiment run")
      _failure_message(crossrunconsume) should include ("does not belong to experiment run")

      When("the owning experiment run consumes the reservation")
      _record(_operation(component, "consumeComparisonReplay",
        "experimentRunId" -> runid,
        "reservationId" -> firstid,
        "observationReference" -> "sanpomap-evidence://comparison/observation-1"
      ))
      val conflictingconsume = _operation(component, "consumeComparisonReplay",
        "experimentRunId" -> runid,
        "reservationId" -> firstid,
        "observationReference" -> "sanpomap-evidence://comparison/other-observation"
      )
      val reservations = _record(_operation(component, "listComparisonReplayReservations",
        "experimentRunId" -> runid
      )).getVector("items").getOrElse(Vector.empty).collect { case value: Record => value }

      Then("consumption is single-use and durable audit state retains each lifecycle disposition")
      _failure_message(conflictingconsume) should include ("already consumed")
      reservations.flatMap(_.getString("status")).toSet shouldBe Set("Consumed", "Cancelled", "Reserved")
    }
  }

  private def _component(
    clock: Clock = Clock.systemUTC(),
    properties: (String, String)*
  ): ExperimentPrimaryComponent = {
    val configuration = Configuration(
      properties.map { case (key, value) => key -> ConfigurationValue.StringValue(value) }.toMap
    )
    val resolvedconfiguration = ResolvedConfiguration(configuration, ConfigurationTrace.empty)
    val base = ExecutionContext.create(clock)
    val datastorespace = new DataStoreSpace().addDataStore(DataStore.inMemorySearchable())
    val entitystorespace = EntityStoreSpace.create(
      resolvedconfiguration
    )
    val scope = ScopeContext.Instance(ScopeContext.Core(
      kind = ScopeKind.Subsystem,
      name = "textus-experiment-spec",
      parent = None,
      observabilityContext = base.observability,
      httpDriverOption = None,
      datastore = Some(DataStoreContext(datastorespace)),
      entitystore = Some(EntityStoreContext(entitystorespace))
    ))
    val subsystem = RuntimeBindingAdmissionFixture.admit(
      new Subsystem(
        name = "textus-experiment-spec",
        scopecontext = Some(scope),
        configuration = resolvedconfiguration
      )
    )
    val bundle = new ComponentFactory().create(ComponentCreate(subsystem, ComponentOrigin.Main))
    subsystem.add(bundle.participants)
    bundle.primary.asInstanceOf[ExperimentPrimaryComponent]
  }

  private def _operation(
    component: ExperimentPrimaryComponent,
    operation: String,
    properties: (String, String)*
  )(using context: ExecutionContext): Consequence[OperationResponse] = {
    val request = Request.of(
      component = ExperimentComponent.name,
      service = "ExperimentManagement",
      operation = operation,
      properties = properties.map { case (name, value) => Property(name, value, None) }.toList
    )
    component.logic.makeOperationRequest(request) match {
      case Consequence.Success(action) => component.logic.executeAction(action.asInstanceOf[Action], context)
      case Consequence.Failure(conclusion) => Consequence.Failure(conclusion)
    }
  }

  private def _record(result: Consequence[OperationResponse]): Record =
    result match {
      case Consequence.Success(OperationResponse.RecordResponse(record)) => record
      case Consequence.Success(other) => fail(s"expected record response but got $other")
      case Consequence.Failure(conclusion) => fail(s"operation failed: ${conclusion.show}")
    }

  private def _failure_message(result: Consequence[OperationResponse]): String =
    result match {
      case Consequence.Failure(conclusion) => conclusion.show
      case Consequence.Success(response) => fail(s"expected failure but got $response")
    }

  private def _external_id(component: String, collection: String, entropy: String): String =
    EntityId(
      major = "textus",
      minor = component.replace('-', '_'),
      collection = EntityCollectionId("textus", component.replace('-', '_'), collection),
      timestamp = Some(Instant.EPOCH),
      entropy = Some(entropy.padTo(32, '0'))
    ).value

  private def _with_privilege(
    context: ExecutionContext,
    privilege: SecurityContext.Privilege
  ): ExecutionContext = {
    val security = SecurityContext(
      principal = new org.goldenport.cncf.context.Principal {
        def id: org.goldenport.cncf.context.PrincipalId = privilege.principalId
        def attributes: Map[String, String] = privilege.attributes + ("authenticated" -> "true")
      },
      capabilities = privilege.capabilities,
      level = privilege.level,
      subjectKind = privilege.subjectKind
    )
    ExecutionContextTestFixture.withSecurityContext(
      context,
      security,
      "textus-experiment-component-factory-spec"
    )
  }
}
