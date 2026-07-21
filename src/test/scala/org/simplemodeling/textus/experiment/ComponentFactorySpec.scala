package org.simplemodeling.textus.experiment

import java.time.Instant

import org.goldenport.Consequence
import org.goldenport.cncf.action.Action
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.context.{DataStoreContext, EntityStoreContext, ExecutionContext, ScopeContext, ScopeKind, SecurityContext}
import org.goldenport.cncf.datastore.{DataStore, DataStoreSpace}
import org.goldenport.cncf.entity.EntityStoreSpace
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ResolvedConfiguration}
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
 * @version Jul. 21, 2026
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

      And("the run can be completed once and no new observation may be appended")
      _record(_operation(component, "completeExperimentRun",
        "experimentRunId" -> runid,
        "status" -> "Completed",
        "completionEvidenceReference" -> "experiment-evidence://run-001/summary"
      )).getString("status") shouldBe Some("Completed")
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
  }

  private def _component(): ExperimentPrimaryComponent = {
    val base = ExecutionContext.create()
    val datastorespace = new DataStoreSpace().addDataStore(DataStore.inMemorySearchable())
    val entitystorespace = EntityStoreSpace.create(
      ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
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
    val subsystem = new Subsystem(
      name = "textus-experiment-spec",
      scopeContext = Some(scope),
      configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
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
    lazy val secured = ExecutionContext.withSecurityContext(
      context,
      SecurityContext(
        principal = new org.goldenport.cncf.context.Principal {
          def id: org.goldenport.cncf.context.PrincipalId = privilege.principalId
          def attributes: Map[String, String] = privilege.attributes + ("authenticated" -> "true")
        },
        capabilities = privilege.capabilities,
        level = privilege.level,
        subjectKind = privilege.subjectKind
      )
    )
    lazy val rebound: ExecutionContext =
      ExecutionContext.withRuntimeContext(secured, runtime)
    lazy val runtime =
      secured.runtime.withUnitOfWorkContext(rebound, "textus-experiment-component-factory-spec")
    rebound
  }
}
