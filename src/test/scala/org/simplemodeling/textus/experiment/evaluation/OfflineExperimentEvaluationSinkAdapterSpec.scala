package org.simplemodeling.textus.experiment.evaluation

import java.time.{Duration, Instant}

import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentId, ComponentInit, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.context.{DataStoreContext, EntityStoreContext, ExecutionContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.datastore.{DataStore, DataStoreSpace}
import org.goldenport.cncf.entity.EntityStoreSpace
import org.goldenport.cncf.operation.evaluation.{CorpusCandidateFact, CorpusCaseReference, CorpusEvaluationCorrelation, CorpusRevisionReference, ExperimentArmReference, ExperimentEvaluationCorrelation, ExperimentObservationFact, ExperimentReference, ExperimentRunReference, OperationEvaluationAttemptId, OperationEvaluationCorrelation, OperationEvaluationDeliveryStatus, OperationEvaluationExecutionId, OperationEvaluationFactId, OperationEvaluationFactSource, OperationEvaluationLabel, OperationEvaluationLimitationKind, OperationEvaluationMeasurement, OperationEvaluationOperationIdentity, OperationEvaluationOutcome, OperationEvaluationStartFact, OperationEvaluationTerminalFact, OperationEvaluationText}
import org.goldenport.cncf.spi.{SpiResolver, SpiSelection}
import org.goldenport.cncf.spi.evaluation.{DeterministicCorpusEvaluationSink, ExperimentEvaluationSink, ExperimentEvaluationSinkSocket}
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.protocol.Protocol
import org.goldenport.schema.DataConfidentiality
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.experiment.ExperimentComponent
import org.simplemodeling.textus.experiment.impl.{ComponentFactory, ExperimentPrimaryComponent}

/*
 * @since   Jul. 24, 2026
 * @version Aug. 14, 2026
 * @author  ASAMI, Tomoharu
 */
final class OfflineExperimentEvaluationSinkAdapterSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "OfflineExperimentEvaluationSinkAdapter" should {
    "retain ordered framework facts and an experiment observation without changing their evidence" in {
      Given("one bounded offline Experiment sink and an assigned evaluation correlation")
      given ExecutionContext = ExecutionContext.create()
      val sink = _success(OfflineExperimentEvaluationSinkAdapter.createC("offline-spec", 8))
      val correlation = _correlation("ordered")
      val start = _start_fact("ordered-start", correlation)
      val terminal = _terminal_fact("ordered-terminal", correlation)
      val observation = _observation_fact("ordered-observation", correlation)

      When("automatic and application-authored facts are delivered in evaluation order")
      val results = Vector(
        _success(sink.recordStart(start)),
        _success(sink.recordTerminal(terminal)),
        _success(sink.submitObservation(observation))
      )

      Then("all facts retain ordering, source, confidentiality, and experiment assignment")
      results.map(_.status) shouldBe Vector.fill(3)(OperationEvaluationDeliveryStatus.Delivered)
      sink.facts.map(_.id) shouldBe Vector(start.id, terminal.id, observation.id)
      sink.facts.map(_.source) shouldBe Vector(
        OperationEvaluationFactSource.Framework,
        OperationEvaluationFactSource.Framework,
        OperationEvaluationFactSource.Application
      )
      sink.facts.map(_.confidentiality) shouldBe Vector.fill(3)(DataConfidentiality.Internal)
      observation.correlation.experiment.flatMap(_.arm).map(_.print) shouldBe Some("arm-control")
      observation.correlation.experiment.flatMap(_.run).map(_.print) shouldBe Some("run-3")
    }

    "preserve one execution and attempt across the offline Corpus case and Experiment arm handoff" in {
      Given("the standard deterministic Corpus sink and Textus-owned offline Experiment adapter")
      given ExecutionContext = ExecutionContext.create()
      val corpussink = _success(DeterministicCorpusEvaluationSink.createC(
        "handoff-spec",
        "textus-corpus-offline",
        Some("offline")
      ))
      val experimentsink = _success(OfflineExperimentEvaluationSinkAdapter.createC("handoff-spec", 8))
      val correlation = _correlation("handoff")
      val start = _start_fact("handoff-start", correlation)
      val terminal = _terminal_fact("handoff-terminal", correlation)
      val candidate = _candidate_fact("handoff-candidate", correlation)
      val observation = _observation_fact("handoff-observation", correlation)

      When("the operation evidence and sink-specific supplemental facts are delivered")
      _success(corpussink.recordStart(start))
      _success(experimentsink.recordStart(start))
      _success(corpussink.recordTerminal(terminal))
      _success(experimentsink.recordTerminal(terminal))
      _success(corpussink.submitCandidate(candidate))
      _success(experimentsink.submitObservation(observation))

      Then("both adapters preserve the same execution, attempt, corpus case, revision, experiment, arm, and run")
      val allfacts = corpussink.facts ++ experimentsink.facts
      allfacts.map(_.correlation.executionId).distinct shouldBe Vector(correlation.executionId)
      allfacts.map(_.correlation.attemptId).distinct shouldBe Vector(correlation.attemptId)
      allfacts.flatMap(_.correlation.corpus.map(_.revision.print)).distinct shouldBe Vector("revision-20260723")
      allfacts.flatMap(_.correlation.corpus.flatMap(_.caseReference).map(_.print)).distinct shouldBe Vector("case-17")
      allfacts.flatMap(_.correlation.experiment.map(_.experiment.print)).distinct shouldBe Vector("experiment-9")
      allfacts.flatMap(_.correlation.experiment.flatMap(_.arm).map(_.print)).distinct shouldBe Vector("arm-control")
      allfacts.flatMap(_.correlation.experiment.flatMap(_.run).map(_.print)).distinct shouldBe Vector("run-3")

      And("the common framework fact ids remain stable across both sink deliveries")
      corpussink.facts.take(2).map(_.id) shouldBe experimentsink.facts.take(2).map(_.id)
    }

    "treat exact redelivery as idempotent and report bounded saturation" in {
      Given("a one-fact offline Experiment sink")
      given ExecutionContext = ExecutionContext.create()
      val sink = _success(OfflineExperimentEvaluationSinkAdapter.createC("offline-spec", 1))
      val correlation = _correlation("bounded")
      val observation = _observation_fact("stable-id", correlation)
      _success(sink.submitObservation(observation))

      When("the exact observation is retried and a second fact is delivered")
      val retry = _success(sink.submitObservation(observation))
      val limited = _success(sink.recordTerminal(_terminal_fact("bounded-terminal", correlation)))

      Then("the retry is delivered without duplication and the second fact is saturated")
      retry.status shouldBe OperationEvaluationDeliveryStatus.Delivered
      limited.status shouldBe OperationEvaluationDeliveryStatus.Limited
      limited.limitations.map(_.kind) shouldBe Vector(OperationEvaluationLimitationKind.Saturated)
      sink.facts shouldBe Vector(observation)

      And("the same bounded behavior holds across generated positive capacities")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(32), Prop.forAll(Gen.choose(1, 24)) { capacity =>
        val generated = _success(OfflineExperimentEvaluationSinkAdapter.createC("generated-spec", capacity))
        val facts = Vector.tabulate(capacity)(index =>
          _observation_fact(s"generated-$capacity-$index", correlation)
        )
        val delivered = facts.map(generated.submitObservation)
        val overflow = _success(generated.recordTerminal(_terminal_fact(s"overflow-$capacity", correlation)))
        delivered.forall(_.toOption.exists(_.status == OperationEvaluationDeliveryStatus.Delivered)) &&
          generated.facts == facts &&
          overflow.status == OperationEvaluationDeliveryStatus.Limited
      })
      checked.passed shouldBe true
    }

    "resolve through the standard Experiment evaluation SPI provider contract" in {
      Given("the Textus-owned offline provider")
      given ExecutionContext = ExecutionContext.create()
      val (providercomponent, subsystem) = _provider_component()
      val consumer = _consumer(
        subsystem,
        SpiSelection(mode = Some("offline")),
        "org.simplemodeling.textus.experiment.evaluation.OfflineExperimentConsumer"
      )

      When("the CNCF SPI contract selects and materializes the provider")
      val resolution = SpiResolver.resolve(Vector(providercomponent, consumer))
      val sink = consumer.experimentEvaluationSink

      Then("the materialized service is the bounded Textus Experiment adapter")
      resolution shouldBe a[Consequence.Success[_]]
      consumer.isSpiInstalled shouldBe true
      sink.sinkIdentityOption.flatMap(_.toRecord.getString("contract")) shouldBe Some("experiment-evaluation-sink")
      sink.sinkIdentityOption.flatMap(_.toRecord.getString("socketComponent")) shouldBe Some("org.simplemodeling.textus.experiment.evaluation.offlineexperimentconsumer")

      And("the emitted provider identity is the generated Experiment component identity")
      sink.sinkIdentityOption.flatMap(_.toRecord.getString("providerComponent")) shouldBe Some("org.simplemodeling.textus.experiment")

      And("an absent mode retains the offline development default")
      val defaultconsumer = _consumer(
        subsystem,
        SpiSelection(),
        "org.simplemodeling.textus.experiment.evaluation.DefaultExperimentConsumer"
      )
      val defaultresolution = SpiResolver.resolve(Vector(providercomponent, defaultconsumer))
      defaultresolution shouldBe a[Consequence.Success[_]]
      defaultconsumer.isSpiInstalled shouldBe true

      And("an explicit non-offline mode leaves the optional socket uninstalled")
      val incompatible = _consumer(
        subsystem,
        SpiSelection(mode = Some("production")),
        "org.simplemodeling.textus.experiment.evaluation.IncompatibleExperimentConsumer"
      )
      val rejected = SpiResolver.resolve(Vector(providercomponent, incompatible))
      rejected shouldBe a[Consequence.Success[_]]
      incompatible.isSpiInstalled shouldBe false
    }
  }

  private val _instant = Instant.parse("2026-07-24T00:00:00Z")

  private final case class ExperimentConsumerComponent(
    selection: SpiSelection
  ) extends Component with ExperimentEvaluationSinkSocket {
    override def spiSelection: SpiSelection = selection
  }

  private def _provider_component(): (ExperimentPrimaryComponent, Subsystem) = {
    val configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
    val base = ExecutionContext.create()
    val datastorespace = new DataStoreSpace().addDataStore(DataStore.inMemorySearchable())
    val entitystorespace = EntityStoreSpace.create(configuration)
    val scope = ScopeContext.Instance(ScopeContext.Core(
      kind = ScopeKind.Subsystem,
      name = "textus-experiment-evaluation-sink-spec",
      parent = None,
      observabilityContext = base.observability,
      httpDriverOption = None,
      datastore = Some(DataStoreContext(datastorespace)),
      entitystore = Some(EntityStoreContext(entitystorespace))
    ))
    val subsystem = new Subsystem(
      name = "textus-experiment-evaluation-sink-spec",
      scopecontext = Some(scope),
      configuration = configuration
    )
    val bundle = new ComponentFactory().create(ComponentCreate(subsystem, ComponentOrigin.Main))
    subsystem.add(bundle.participants)
    (bundle.primary.asInstanceOf[ExperimentPrimaryComponent], subsystem)
  }

  private def _consumer(
    subsystem: Subsystem,
    selection: SpiSelection,
    componentname: String
  ): ExperimentConsumerComponent = {
    val consumer = ExperimentConsumerComponent(selection)
    val componentid = ComponentId(componentname)
    val core = Component.Core.create(
      name = componentid.name,
      componentId = componentid,
      instanceId = ComponentInstanceId.default(componentid),
      protocol = Protocol.empty,
      jobEngine = subsystem.jobEngine
    )
    consumer.initialize(ComponentInit(subsystem, core, ComponentOrigin.Builtin))
    consumer
  }

  private def _correlation(entropy: String): OperationEvaluationCorrelation = {
    val revision = _success(CorpusRevisionReference.parseC("revision-20260723"))
    val corpuscase = _success(CorpusCaseReference.parseC("case-17"))
    val experiment = _success(ExperimentReference.parseC("experiment-9"))
    val arm = _success(ExperimentArmReference.parseC("arm-control"))
    val run = _success(ExperimentRunReference.parseC("run-3"))
    OperationEvaluationCorrelation(
      OperationEvaluationExecutionId("spec", "execution", Some(_instant), Some(entropy)),
      OperationEvaluationAttemptId("spec", "attempt", Some(_instant), Some(entropy)),
      _success(OperationEvaluationOperationIdentity.createC("sample", "evaluation", "evaluate")),
      corpus = Some(CorpusEvaluationCorrelation.create(revision, Some(corpuscase))),
      experiment = Some(_success(ExperimentEvaluationCorrelation.createC(
        experiment,
        Some(arm),
        Some(run),
        Some(revision)
      )))
    )
  }

  private def _start_fact(
    entropy: String,
    correlation: OperationEvaluationCorrelation
  ): OperationEvaluationStartFact =
    OperationEvaluationStartFact.create(_fact_id(entropy), correlation, _instant)

  private def _terminal_fact(
    entropy: String,
    correlation: OperationEvaluationCorrelation
  ): OperationEvaluationTerminalFact =
    _success(OperationEvaluationTerminalFact.createC(
      _fact_id(entropy),
      correlation,
      _instant.plusMillis(25),
      OperationEvaluationOutcome.Success,
      Duration.ofMillis(25)
    ))

  private def _candidate_fact(
    entropy: String,
    correlation: OperationEvaluationCorrelation
  ): CorpusCandidateFact =
    _success(CorpusCandidateFact.createC(
      _fact_id(entropy),
      correlation,
      _instant.plusMillis(10),
      Some(_success(OperationEvaluationText.parseC("candidate accepted"))),
      Vector(_success(OperationEvaluationLabel.createC("decision", "review")))
    ))

  private def _observation_fact(
    entropy: String,
    correlation: OperationEvaluationCorrelation
  ): ExperimentObservationFact =
    _success(ExperimentObservationFact.createC(
      _fact_id(entropy),
      correlation,
      _instant.plusMillis(20),
      Vector(_success(OperationEvaluationMeasurement.createC("score", BigDecimal("0.92")))),
      Vector(_success(OperationEvaluationLabel.createC("verdict", "pass")))
    ))

  private def _fact_id(entropy: String): OperationEvaluationFactId =
    OperationEvaluationFactId("spec", "fact", Some(_instant), Some(entropy))

  private def _success[A](consequence: Consequence[A]): A =
    consequence.toOption.getOrElse(fail(consequence.toString))
}
