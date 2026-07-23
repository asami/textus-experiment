package org.simplemodeling.textus.experiment.evaluation

import scala.collection.mutable

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.operation.evaluation.{ExperimentObservationFact, OperationEvaluationDeliveryResult, OperationEvaluationDeliveryStatus, OperationEvaluationFact, OperationEvaluationFactId, OperationEvaluationLimitation, OperationEvaluationLimitationKind, OperationEvaluationSinkIdentity, OperationEvaluationStartFact, OperationEvaluationTerminalFact}
import org.goldenport.cncf.spi.{SpiContract, SpiProvider, SpiSelection}
import org.goldenport.cncf.spi.evaluation.ExperimentEvaluationSink

/*
 * Bounded, non-persistent Experiment evaluation sink for development and
 * offline verification. Production observation retention requires a separate
 * persistent provider.
 *
 * @since   Jul. 24, 2026
 * @version Jul. 24, 2026
 * @author  ASAMI, Tomoharu
 */
final class OfflineExperimentEvaluationSinkAdapter private (
  sinkidentity: OperationEvaluationSinkIdentity,
  maximumfactcount: Int
) extends ExperimentEvaluationSink {
  private val _facts = mutable.LinkedHashMap.empty[OperationEvaluationFactId, OperationEvaluationFact]

  override val sinkIdentityOption: Option[OperationEvaluationSinkIdentity] = Some(sinkidentity)

  def sinkIdentity: OperationEvaluationSinkIdentity = sinkidentity
  def maximumFactCount: Int = maximumfactcount
  def facts: Vector[OperationEvaluationFact] = synchronized(_facts.values.toVector)

  def recordStart(fact: OperationEvaluationStartFact)(using ExecutionContext): Consequence[OperationEvaluationDeliveryResult] =
    _record(fact)

  def recordTerminal(fact: OperationEvaluationTerminalFact)(using ExecutionContext): Consequence[OperationEvaluationDeliveryResult] =
    _record(fact)

  def submitObservation(fact: ExperimentObservationFact)(using ExecutionContext): Consequence[OperationEvaluationDeliveryResult] =
    _record(fact)

  private def _record(fact: OperationEvaluationFact): Consequence[OperationEvaluationDeliveryResult] = synchronized {
    val key = fact.id
    _facts.get(key) match {
      case Some(current) if current == fact =>
        _result(fact, OperationEvaluationDeliveryStatus.Delivered)
      case Some(_) =>
        Consequence.stateConflict(s"operation evaluation fact id already has different content: ${key.toString}")
      case None if _facts.size >= maximumfactcount =>
        _result(
          fact,
          OperationEvaluationDeliveryStatus.Limited,
          Vector(OperationEvaluationLimitation(OperationEvaluationLimitationKind.Saturated))
        )
      case None =>
        _facts.put(key, fact)
        _result(fact, OperationEvaluationDeliveryStatus.Delivered)
    }
  }

  private def _result(
    fact: OperationEvaluationFact,
    status: OperationEvaluationDeliveryStatus,
    limitations: Vector[OperationEvaluationLimitation] = Vector.empty
  ): Consequence[OperationEvaluationDeliveryResult] =
    OperationEvaluationDeliveryResult.createC(
      fact.id,
      sinkidentity,
      status,
      limitations,
      fact.confidentiality
    )
}

object OfflineExperimentEvaluationSinkAdapter {
  val DEFAULT_MAXIMUM_FACT_COUNT: Int = 4096
  val PROVIDER_COMPONENT = "textus-experiment"
  val PROVIDER_INSTANCE = "offline"

  def createC(
    socketcomponent: String,
    maximumfactcount: Int = DEFAULT_MAXIMUM_FACT_COUNT
  ): Consequence[OfflineExperimentEvaluationSinkAdapter] =
    if (maximumfactcount <= 0)
      Consequence.argumentInvalid("maximumFactCount", "positive fact capacity", maximumfactcount)
    else
      OperationEvaluationSinkIdentity
        .createC(
          ExperimentEvaluationSink.CONTRACT_NAME,
          socketcomponent,
          PROVIDER_COMPONENT,
          Some(PROVIDER_INSTANCE)
        )
        .map(new OfflineExperimentEvaluationSinkAdapter(_, maximumfactcount))
}

final case class OfflineExperimentEvaluationSinkProvider(
  maximumfactcount: Int = OfflineExperimentEvaluationSinkAdapter.DEFAULT_MAXIMUM_FACT_COUNT
) extends SpiProvider[ExperimentEvaluationSink] {
  def supports(
    contract: SpiContract[ExperimentEvaluationSink],
    selection: SpiSelection
  )(using ExecutionContext): Boolean =
    contract.name == ExperimentEvaluationSink.CONTRACT_NAME &&
      contract.runtimeClass == classOf[ExperimentEvaluationSink] &&
      selection.mode.forall(_ == "offline")

  def provide(
    contract: SpiContract[ExperimentEvaluationSink],
    selection: SpiSelection
  )(using ExecutionContext): Consequence[ExperimentEvaluationSink] =
    OfflineExperimentEvaluationSinkAdapter.createC("offline-consumer", maximumfactcount)
}
