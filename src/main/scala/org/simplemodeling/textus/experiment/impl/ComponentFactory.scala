package org.simplemodeling.textus.experiment.impl

import java.time.Instant
import cats.implicits.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, FunctionalActionCall}
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentId}
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.entity.{EntityQuery, EntitySearchScope}
import org.goldenport.cncf.unitofwork.ExecUowM
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.EntityId
import org.simplemodeling.textus.experiment.ExperimentComponent
import org.simplemodeling.textus.experiment.entity.{ComparisonReplayReservation, Experiment, ExperimentArm, ExperimentObservation, ExperimentRun}
import org.simplemodeling.textus.experiment.entity.create.{ComparisonReplayReservation as ComparisonReplayReservationCreate, Experiment as ExperimentCreate, ExperimentArm as ExperimentArmCreate, ExperimentObservation as ExperimentObservationCreate, ExperimentRun as ExperimentRunCreate}
import org.simplemodeling.textus.experiment.entity.update.{ComparisonReplayReservation as ComparisonReplayReservationUpdate, Experiment as ExperimentUpdate, ExperimentRun as ExperimentRunUpdate}
import org.simplemodeling.textus.experiment.datatype.{ComparisonReplayReservationStatus, ExperimentEvidenceReference, ExperimentRunStatus, ExperimentStatus}

/*
 * @since   Jul. 21, 2026
 * @version Jul. 23, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentFactory extends Component.BundleFactory {
  def primaryFactory: Component.PrimaryComponentFactory =
    ExperimentPrimaryFactory

  override def componentletFactories: Vector[Component.ComponentletFactory] =
    Vector.empty
}

abstract class ExperimentParticipantFactoryBase extends ExperimentComponent.Factory {
  protected final val shared_services =
    Vector(ExperimentComponent.ExperimentManagementService)

  protected final def component_core(
    name: String,
    componentid: ComponentId
  ): Component.Core =
    spec_create(name, componentid, shared_services)

  override val ExperimentManagement: ExperimentComponent.ExperimentManagementServiceFactory =
    DefaultExperimentManagementServiceFactory()
  override val aggregate: ExperimentComponent.AggregateServiceFactory = AggregateServiceFactoryImpl()
  override val view: ExperimentComponent.ViewServiceFactory = ViewServiceFactoryImpl()
  override val entity: ExperimentComponent.EntityServiceFactory = DefaultEntityServiceFactory()
}

final class ExperimentPrimaryComponent extends ExperimentComponent {
  override def mcpReadyServices: Set[String] =
    Set.empty
}

object ExperimentPrimaryFactory extends ExperimentParticipantFactoryBase with Component.PrimaryComponentFactory {
  override protected def create_Component(params: ComponentCreate): Component =
    new ExperimentPrimaryComponent()

  override protected def create_Core(
    params: ComponentCreate,
    comp: Component
  ): Component.Core =
    component_core(ExperimentComponent.name, ExperimentComponent.componentId)
}

final class DefaultExperimentManagementServiceFactory extends ExperimentComponent.ExperimentManagementServiceFactory {
  import ExperimentComponent.ExperimentManagementService.*

  override def createDefineExperimentActionCall(core: ActionCall.Core, action: DefineExperiment): DefineExperimentActionCall =
    DefineExperimentActionCallImpl(core, action)

  override def createDefineExperimentArmActionCall(core: ActionCall.Core, action: DefineExperimentArm): DefineExperimentArmActionCall =
    DefineExperimentArmActionCallImpl(core, action)

  override def createActivateExperimentActionCall(core: ActionCall.Core, action: ActivateExperiment): ActivateExperimentActionCall =
    ActivateExperimentActionCallImpl(core, action)

  override def createStartExperimentActionCall(core: ActionCall.Core, action: StartExperiment): StartExperimentActionCall =
    StartExperimentActionCallImpl(core, action)

  override def createReserveComparisonReplayActionCall(core: ActionCall.Core, action: ReserveComparisonReplay): ReserveComparisonReplayActionCall =
    ReserveComparisonReplayActionCallImpl(core, action)

  override def createConsumeComparisonReplayActionCall(core: ActionCall.Core, action: ConsumeComparisonReplay): ConsumeComparisonReplayActionCall =
    ConsumeComparisonReplayActionCallImpl(core, action)

  override def createCancelComparisonReplayActionCall(core: ActionCall.Core, action: CancelComparisonReplay): CancelComparisonReplayActionCall =
    CancelComparisonReplayActionCallImpl(core, action)

  override def createExpireComparisonReplayReservationsActionCall(core: ActionCall.Core, action: ExpireComparisonReplayReservations): ExpireComparisonReplayReservationsActionCall =
    ExpireComparisonReplayReservationsActionCallImpl(core, action)

  override def createListComparisonReplayReservationsActionCall(core: ActionCall.Core, action: ListComparisonReplayReservations): ListComparisonReplayReservationsActionCall =
    ListComparisonReplayReservationsActionCallImpl(core, action)

  override def createCompleteExperimentRunActionCall(core: ActionCall.Core, action: CompleteExperimentRun): CompleteExperimentRunActionCall =
    CompleteExperimentRunActionCallImpl(core, action)

  override def createRecordObservationActionCall(core: ActionCall.Core, action: RecordObservation): RecordObservationActionCall =
    RecordObservationActionCallImpl(core, action)

  override def createListExperimentRunsActionCall(core: ActionCall.Core, action: ListExperimentRuns): ListExperimentRunsActionCall =
    ListExperimentRunsActionCallImpl(core, action)

  override def createListExperimentObservationsActionCall(core: ActionCall.Core, action: ListExperimentObservations): ListExperimentObservationsActionCall =
    ListExperimentObservationsActionCallImpl(core, action)

  override def createSummarizeExperimentRunActionCall(core: ActionCall.Core, action: SummarizeExperimentRun): SummarizeExperimentRunActionCall =
    SummarizeExperimentRunActionCallImpl(core, action)
}

object DefaultExperimentManagementServiceFactory {
  def apply(): DefaultExperimentManagementServiceFactory = new DefaultExperimentManagementServiceFactory()
}

private trait ExperimentActionSupport {
  self: FunctionalActionCall =>

  protected final def all_experiments: ExecUowM[Vector[Experiment]] =
    _all[Experiment](org.simplemodeling.textus.experiment.entity.query.Experiment.collectionId)

  protected final def all_arms: ExecUowM[Vector[ExperimentArm]] =
    _all[ExperimentArm](org.simplemodeling.textus.experiment.entity.query.ExperimentArm.collectionId)

  protected final def all_runs: ExecUowM[Vector[ExperimentRun]] =
    _all[ExperimentRun](org.simplemodeling.textus.experiment.entity.query.ExperimentRun.collectionId)

  protected final def all_reservations: ExecUowM[Vector[ComparisonReplayReservation]] =
    _all[ComparisonReplayReservation](org.simplemodeling.textus.experiment.entity.query.ComparisonReplayReservation.collectionId)

  protected final def all_observations: ExecUowM[Vector[ExperimentObservation]] =
    _all[ExperimentObservation](org.simplemodeling.textus.experiment.entity.query.ExperimentObservation.collectionId)

  private def _all[A](collectionid: org.simplemodeling.model.datatype.EntityCollectionId)(using
    persistent: org.goldenport.cncf.entity.EntityPersistent[A]
  ): ExecUowM[Vector[A]] =
    entity_search_internal[A](EntityQuery(
      collectionid,
      Query.fromRecord(Record.empty),
      EntitySearchScope.Store
    )).map(_.data.toVector)

  protected final def required_entity_id(record: Record, name: String): Consequence[EntityId] =
    record.getAs[EntityId](name)
      .map(Consequence.success)
      .getOrElse(Consequence.failRecordNotFound(name, record))

  protected final def page[A](items: Vector[A], record: Record): Vector[A] = {
    val offset = record.getInt("offset").getOrElse(0).max(0)
    val limit = record.getInt("limit").getOrElse(100).max(0)
    items.drop(offset).take(limit)
  }

  protected final def experiment_record(value: Experiment): Record =
    value.toRecord()
      .upsertSingle("id", value.id.value)
      .upsertSingle("corpusRevisionId", value.corpusRevisionId.value)

  protected final def arm_record(value: ExperimentArm): Record =
    value.toRecord()
      .upsertSingle("id", value.id.value)
      .upsertSingle("experimentId", value.experimentId.value)

  protected final def run_record(value: ExperimentRun): Record =
    value.toRecord()
      .upsertSingle("id", value.id.value)
      .upsertSingle("experimentId", value.experimentId.value)
      .upsertSingle("corpusRevisionId", value.corpusRevisionId.value)

  protected final def reservation_record(value: ComparisonReplayReservation): Record =
    value.toRecord()
      .upsertSingle("id", value.id.value)
      .upsertSingle("experimentRunId", value.experimentRunId.value)

  protected final def expire_reservations(
    values: Vector[ComparisonReplayReservation],
    now: Instant
  ): ExecUowM[Vector[ComparisonReplayReservation]] =
    values.foldLeft(exec_pure(Vector.empty[ComparisonReplayReservation])) { (z, current) =>
      for {
        resolved <- z
        updated <- if (current.status.value == "Reserved" && !current.expiresAt.isAfter(now))
          for {
            patch <- exec_from(ComparisonReplayReservationUpdate.createC(Record.dataAuto("status" -> "Expired")))
            _ <- entity_update(current.id, patch)
          } yield current.copy(status = ComparisonReplayReservationStatus("Expired"))
        else
          exec_pure(current)
      } yield resolved :+ updated
    }

  protected final def observation_record(value: ExperimentObservation): Record =
    value.toRecord()
      .upsertSingle("id", value.id.value)
      .upsertSingle("experimentRunId", value.experimentRunId.value)
      .upsertSingle("experimentArmId", value.experimentArmId.value)
      .upsertSingle("corpusCaseId", value.corpusCaseId.value)
}

private final case class DefineExperimentActionCallImpl(
  core: ActionCall.Core,
  override val action: ExperimentComponent.ExperimentManagementService.DefineExperiment
) extends ExperimentComponent.ExperimentManagementService.DefineExperimentActionCall
    with ExperimentActionSupport {
  protected def build_Program: ExecUowM[OperationResponse] =
    for {
      input <- exec_from(ExperimentCreate.createC(action.record.upsertSingle("status", "Draft")))
      _ <- exec_from(_validate(input))
      experiments <- all_experiments
      existing = experiments.find(_.experimentKey == input.experimentKey)
      response <- existing match {
        case Some(current) if _same(current, input) =>
          exec_pure(_response(current.id, current.experimentKey.value, current.name.value))
        case Some(_) =>
          exec_from(Consequence.stateConflict(
            s"Experiment '${input.experimentKey.value}' is already defined with different immutable content."
          ))
        case None =>
          entity_create(input).map(created => _response(created.id, input.experimentKey.value, input.name.map(_.value).getOrElse("")))
      }
    } yield response

  private def _validate(input: ExperimentCreate): Consequence[Unit] =
    if (input.experimentKey.value.trim.isEmpty)
      Consequence.operationInvalid("experimentKey must not be empty")
    else if (input.acceptanceOperation.value.trim.isEmpty)
      Consequence.operationInvalid("acceptanceOperation must not be empty")
    else
      Consequence.unit

  private def _same(current: Experiment, input: ExperimentCreate): Boolean =
    input.name.contains(current.name) &&
      current.corpusRevisionId == input.corpusRevisionId &&
      current.acceptanceOperation == input.acceptanceOperation &&
      current.description == input.description

  private def _response(id: EntityId, key: String, name: String): OperationResponse =
    OperationResponse(Record.dataAuto("id" -> id.value, "experimentKey" -> key, "name" -> name))
}

private final case class DefineExperimentArmActionCallImpl(
  core: ActionCall.Core,
  override val action: ExperimentComponent.ExperimentManagementService.DefineExperimentArm
) extends ExperimentComponent.ExperimentManagementService.DefineExperimentArmActionCall
    with ExperimentActionSupport {
  protected def build_Program: ExecUowM[OperationResponse] =
    for {
      input <- exec_from(ExperimentArmCreate.createC(action.record))
      _ <- exec_from(_validate(input))
      experiment <- entity_load_option_internal[Experiment](input.experimentId)
      currentexperiment <- exec_from(experiment.map(Consequence.success).getOrElse(
        Consequence.entityNotFound(s"Experiment '${input.experimentId}' does not exist.")
      ))
      arms <- all_arms
      existing = arms.find(x => x.experimentId == input.experimentId && x.armKey == input.armKey)
      response <- existing match {
        case Some(current) if _same(current, input) =>
          exec_pure(OperationResponse(Record.dataAuto("id" -> current.id.value, "armKey" -> current.armKey.value)))
        case Some(_) =>
          exec_from(Consequence.stateConflict(
            s"Experiment arm '${input.armKey.value}' already has different content."
          ))
        case None if currentexperiment.status.value != "Draft" =>
          exec_from(Consequence.stateConflict("Experiment arms can only be added while the experiment is Draft."))
        case None =>
          entity_create(input).map(created => OperationResponse(Record.dataAuto("id" -> created.id.value, "armKey" -> input.armKey.value)))
      }
    } yield response

  private def _validate(input: ExperimentArmCreate): Consequence[Unit] =
    if (input.armKey.value.trim.isEmpty)
      Consequence.operationInvalid("armKey must not be empty")
    else if (input.executionPlanReference.value.trim.isEmpty)
      Consequence.operationInvalid("executionPlanReference must not be empty")
    else
      Consequence.unit

  private def _same(current: ExperimentArm, input: ExperimentArmCreate): Boolean =
    input.name.contains(current.name) && current.executionPlanReference == input.executionPlanReference
}

private final case class ActivateExperimentActionCallImpl(
  core: ActionCall.Core,
  override val action: ExperimentComponent.ExperimentManagementService.ActivateExperiment
) extends ExperimentComponent.ExperimentManagementService.ActivateExperimentActionCall
    with ExperimentActionSupport {
  protected def build_Program: ExecUowM[OperationResponse] =
    for {
      id <- exec_from(required_entity_id(action.record, "experimentId"))
      experiment <- entity_load_option_internal[Experiment](id)
      current <- exec_from(experiment.map(Consequence.success).getOrElse(
        Consequence.entityNotFound(s"Experiment '$id' does not exist.")
      ))
      arms <- all_arms
      response <- current.status.value match {
        case "Active" => exec_pure(_response(current))
        case "Draft" if arms.exists(_.experimentId == current.id) =>
          val updated = current.copy(status = ExperimentStatus("Active"))
          for {
            patch <- exec_from(ExperimentUpdate.createC(Record.dataAuto("status" -> "Active")))
            _ <- entity_update(current.id, patch)
          } yield _response(updated)
        case "Draft" =>
          exec_from(Consequence.stateConflict("An experiment requires at least one arm before activation."))
        case other =>
          exec_from(Consequence.stateConflict(s"Experiment in status '$other' cannot be activated."))
      }
    } yield response

  private def _response(value: Experiment): OperationResponse =
    OperationResponse(Record.dataAuto("id" -> value.id.value, "status" -> value.status.value))
}

private final case class StartExperimentActionCallImpl(
  core: ActionCall.Core,
  override val action: ExperimentComponent.ExperimentManagementService.StartExperiment
) extends ExperimentComponent.ExperimentManagementService.StartExperimentActionCall
    with ExperimentActionSupport {
  protected def build_Program: ExecUowM[OperationResponse] =
    for {
      experimentid <- exec_from(required_entity_id(action.record, "experimentId"))
      experiment <- entity_load_option_internal[Experiment](experimentid)
      current <- exec_from(experiment.map(Consequence.success).getOrElse(
        Consequence.entityNotFound(s"Experiment '$experimentid' does not exist.")
      ))
      _ <- if (current.status.value == "Active") exec_pure(())
        else exec_from(Consequence.stateConflict("Only an Active experiment can start a run."))
      inputrecord = action.record
        .upsertSingle("corpusRevisionId", current.corpusRevisionId.value)
        .upsertSingle("status", "Running")
      input <- exec_from(ExperimentRunCreate.createC(inputrecord))
      _ <- if (input.runReference.value.trim.nonEmpty) exec_pure(())
        else exec_from(Consequence.operationInvalid("runReference must not be empty"))
      runs <- all_runs
      existing = runs.find(x => x.experimentId == current.id && x.runReference == input.runReference)
      response <- existing match {
        case Some(run) => exec_pure(_response(run.id, run.runReference.value))
        case None => entity_create(input).map(created => _response(created.id, input.runReference.value))
      }
    } yield response

  private def _response(id: EntityId, reference: String): OperationResponse =
    OperationResponse(Record.dataAuto("id" -> id.value, "runReference" -> reference))
}

private final case class ReserveComparisonReplayActionCallImpl(
  core: ActionCall.Core,
  override val action: ExperimentComponent.ExperimentManagementService.ReserveComparisonReplay
) extends ExperimentComponent.ExperimentManagementService.ReserveComparisonReplayActionCall
    with ExperimentActionSupport {
  protected def build_Program: ExecUowM[OperationResponse] =
    for {
      policy <- exec_from(ComparisonReplayReservationSupport.policy(core))
      runid <- exec_from(required_entity_id(action.record, "experimentRunId"))
      run <- entity_load_option_internal[ExperimentRun](runid)
      currentrun <- exec_from(run.map(Consequence.success).getOrElse(
        Consequence.entityNotFound(s"Experiment run '$runid' does not exist.")
      ))
      _ <- if (currentrun.status.value == "Running") exec_pure(())
        else exec_from(Consequence.stateConflict("Comparison replay requires a Running experiment run."))
      now = execution_clock.instant()
      input <- exec_from(ComparisonReplayReservationCreate.createC(action.record
        .upsertSingle("status", "Reserved")
        .upsertSingle("reservedAt", now.toString)
        .upsertSingle("expiresAt", now.plusSeconds(policy.reservationTtlSeconds).toString)
      ))
      _ <- if (input.reservationKey.value.trim.nonEmpty) exec_pure(())
        else exec_from(Consequence.operationInvalid("reservationKey must not be empty"))
      _ <- if (input.replayCount > 0) exec_pure(())
        else exec_from(Consequence.operationInvalid("replayCount must be a positive integer"))
      _ <- if (input.budgetMicrounits > 0) exec_pure(())
        else exec_from(Consequence.operationInvalid("budgetMicrounits must be a positive long"))
      reservations <- all_reservations
      resolved <- expire_reservations(reservations, now)
      existing = resolved.find(_.reservationKey == input.reservationKey)
      response <- existing match {
        case Some(value) if _same(value, input) && value.status.value == "Reserved" =>
          exec_pure(_response(value))
        case Some(value) =>
          exec_from(Consequence.stateConflict(
            s"Comparison replay reservation '${value.reservationKey.value}' is already terminal or has different immutable content."
          ))
        case None =>
          val allocated = resolved.filter(x =>
            x.experimentRunId == input.experimentRunId && Set("Reserved", "Consumed").contains(x.status.value)
          )
          val replaycount = allocated.map(_.replayCount.toLong).sum + input.replayCount
          val budget = allocated.map(_.budgetMicrounits).sum + input.budgetMicrounits
          if (replaycount > policy.maximumReplayCount)
            exec_from(Consequence.configurationInvalid(
              s"Comparison replay count exceeds per-run operator maximum: $replaycount > ${policy.maximumReplayCount}"
            ))
          else if (budget > policy.maximumBudgetMicrounits)
            exec_from(Consequence.configurationInvalid(
              s"Comparison replay budget exceeds per-run operator maximum: $budget > ${policy.maximumBudgetMicrounits}"
            ))
          else
            entity_create(input).map(created => _response(ComparisonReplayReservation(
              id = created.id,
              experimentRunId = input.experimentRunId,
              reservationKey = input.reservationKey,
              replayCount = input.replayCount,
              budgetMicrounits = input.budgetMicrounits,
              status = input.status,
              reservedAt = input.reservedAt,
              expiresAt = input.expiresAt,
              observationReference = input.observationReference,
              cancellationEvidenceReference = input.cancellationEvidenceReference
            )))
      }
    } yield response

  private def _same(current: ComparisonReplayReservation, input: ComparisonReplayReservationCreate): Boolean =
    current.experimentRunId == input.experimentRunId &&
      current.replayCount == input.replayCount &&
      current.budgetMicrounits == input.budgetMicrounits

  private def _response(value: ComparisonReplayReservation): OperationResponse =
    OperationResponse(Record.dataAuto(
      "id" -> value.id.value,
      "status" -> value.status.value,
      "expiresAt" -> value.expiresAt.toString,
      "replayCount" -> value.replayCount,
      "budgetMicrounits" -> value.budgetMicrounits
    ))
}

private final case class ConsumeComparisonReplayActionCallImpl(
  core: ActionCall.Core,
  override val action: ExperimentComponent.ExperimentManagementService.ConsumeComparisonReplay
) extends ExperimentComponent.ExperimentManagementService.ConsumeComparisonReplayActionCall
    with ExperimentActionSupport {
  protected def build_Program: ExecUowM[OperationResponse] =
    for {
      runid <- exec_from(required_entity_id(action.record, "experimentRunId"))
      id <- exec_from(required_entity_id(action.record, "reservationId"))
      reference <- exec_from(ComparisonReplayReservationSupport.requiredOpaqueReference(action.record, "observationReference"))
      reservation <- entity_load_option_internal[ComparisonReplayReservation](id)
      current <- exec_from(reservation.map(Consequence.success).getOrElse(
        Consequence.entityNotFound(s"Comparison replay reservation '$id' does not exist.")
      ))
      _ <- if (current.experimentRunId == runid) exec_pure(())
      else exec_from(Consequence.stateConflict(
        s"Comparison replay reservation '$id' does not belong to experiment run '$runid'."
      ))
      response <- current.status.value match {
        case "Reserved" if !current.expiresAt.isAfter(execution_clock.instant()) =>
          for {
            patch <- exec_from(ComparisonReplayReservationUpdate.createC(Record.dataAuto("status" -> "Expired")))
            _ <- entity_update(current.id, patch)
            response <- exec_from(Consequence.stateConflict[OperationResponse]("Comparison replay reservation has expired."))
          } yield response
        case "Reserved" =>
          val updated = current.copy(
            status = ComparisonReplayReservationStatus("Consumed"),
            observationReference = Some(reference)
          )
          for {
            patch <- exec_from(ComparisonReplayReservationUpdate.createC(Record.dataAuto(
              "status" -> "Consumed",
              "observationReference" -> reference.value
            )))
            _ <- entity_update(current.id, patch)
          } yield _response(updated)
        case "Consumed" if current.observationReference.contains(reference) =>
          exec_pure(_response(current))
        case "Consumed" =>
          exec_from(Consequence.stateConflict("Comparison replay reservation is already consumed with different observation evidence."))
        case status =>
          exec_from(Consequence.stateConflict(s"Comparison replay reservation in status '$status' cannot be consumed."))
      }
    } yield response

  private def _response(value: ComparisonReplayReservation): OperationResponse =
    OperationResponse(Record.dataAuto("id" -> value.id.value, "status" -> value.status.value))
}

private final case class CancelComparisonReplayActionCallImpl(
  core: ActionCall.Core,
  override val action: ExperimentComponent.ExperimentManagementService.CancelComparisonReplay
) extends ExperimentComponent.ExperimentManagementService.CancelComparisonReplayActionCall
    with ExperimentActionSupport {
  protected def build_Program: ExecUowM[OperationResponse] =
    for {
      id <- exec_from(required_entity_id(action.record, "reservationId"))
      reference <- exec_from(ComparisonReplayReservationSupport.optionalOpaqueReference(action.record, "cancellationEvidenceReference"))
      reservation <- entity_load_option_internal[ComparisonReplayReservation](id)
      current <- exec_from(reservation.map(Consequence.success).getOrElse(
        Consequence.entityNotFound(s"Comparison replay reservation '$id' does not exist.")
      ))
      response <- current.status.value match {
        case "Reserved" if !current.expiresAt.isAfter(execution_clock.instant()) =>
          for {
            patch <- exec_from(ComparisonReplayReservationUpdate.createC(Record.dataAuto("status" -> "Expired")))
            _ <- entity_update(current.id, patch)
            response <- exec_from(Consequence.stateConflict[OperationResponse]("Comparison replay reservation has expired."))
          } yield response
        case "Reserved" =>
          val updated = current.copy(
            status = ComparisonReplayReservationStatus("Cancelled"),
            cancellationEvidenceReference = reference
          )
          for {
            patch <- exec_from(ComparisonReplayReservationUpdate.createC(Record.dataAuto(
              "status" -> "Cancelled",
              "cancellationEvidenceReference" -> reference.map(_.value)
            )))
            _ <- entity_update(current.id, patch)
          } yield _response(updated)
        case "Cancelled" if current.cancellationEvidenceReference == reference =>
          exec_pure(_response(current))
        case "Cancelled" =>
          exec_from(Consequence.stateConflict("Comparison replay reservation is already cancelled with different evidence."))
        case status =>
          exec_from(Consequence.stateConflict(s"Comparison replay reservation in status '$status' cannot be cancelled."))
      }
    } yield response

  private def _response(value: ComparisonReplayReservation): OperationResponse =
    OperationResponse(Record.dataAuto("id" -> value.id.value, "status" -> value.status.value))
}

private final case class ExpireComparisonReplayReservationsActionCallImpl(
  core: ActionCall.Core,
  override val action: ExperimentComponent.ExperimentManagementService.ExpireComparisonReplayReservations
) extends ExperimentComponent.ExperimentManagementService.ExpireComparisonReplayReservationsActionCall
    with ExperimentActionSupport {
  protected def build_Program: ExecUowM[OperationResponse] =
    for {
      reservations <- all_reservations
      expired <- expire_reservations(reservations, execution_clock.instant())
      count = reservations.zip(expired).count { case (before, after) =>
        before.status.value == "Reserved" && after.status.value == "Expired"
      }
    } yield OperationResponse(Record.dataAuto("expiredCount" -> count))
}

private final case class ListComparisonReplayReservationsActionCallImpl(
  core: ActionCall.Core,
  override val action: ExperimentComponent.ExperimentManagementService.ListComparisonReplayReservations
) extends ExperimentComponent.ExperimentManagementService.ListComparisonReplayReservationsActionCall
    with ExperimentActionSupport {
  protected def build_Program: ExecUowM[OperationResponse] =
    for {
      reservations <- all_reservations
      runid = action.record.getAs[EntityId]("experimentRunId")
      status = action.record.getString("status")
      selected = page(reservations
        .filter(x => runid.forall(_ == x.experimentRunId))
        .filter(x => status.forall(_ == x.status.value))
        .sortBy(x => (x.experimentRunId.value, x.reservedAt.toString, x.reservationKey.value)), action.record)
    } yield OperationResponse(Record.dataAuto("items" -> selected.map(reservation_record)))
}

private object ComparisonReplayReservationSupport {
  private val _opaque_reference = "[a-z][a-z0-9+.-]*://[A-Za-z0-9._~/-]{1,255}".r

  def policy(core: ActionCall.Core): Consequence[ComparisonReplaySchedulerConfig] =
    core.component.flatMap(_.subsystem)
      .map(value => ComparisonReplaySchedulerConfig.fromConfiguration(value.configuration))
      .getOrElse(Consequence.serviceUnavailable("Experiment subsystem is not initialized."))

  def requiredOpaqueReference(record: Record, name: String): Consequence[ExperimentEvidenceReference] =
    record.getString(name).map(_.trim).filter(_.nonEmpty)
      .map(_opaque_reference_c)
      .getOrElse(Consequence.failRecordNotFound(name, record))

  def optionalOpaqueReference(record: Record, name: String): Consequence[Option[ExperimentEvidenceReference]] =
    record.getString(name).map(_.trim).filter(_.nonEmpty) match {
      case Some(value) => _opaque_reference_c(value).map(Some.apply)
      case None => Consequence.success(None)
    }

  private def _opaque_reference_c(value: String): Consequence[ExperimentEvidenceReference] =
    if (_opaque_reference.matches(value)) Consequence.success(ExperimentEvidenceReference(value))
    else Consequence.configurationInvalid(s"Comparison replay evidence reference is not an opaque safe reference: $value")
}

private final case class CompleteExperimentRunActionCallImpl(
  core: ActionCall.Core,
  override val action: ExperimentComponent.ExperimentManagementService.CompleteExperimentRun
) extends ExperimentComponent.ExperimentManagementService.CompleteExperimentRunActionCall
    with ExperimentActionSupport {
  private val _terminal_statuses = Set("Completed", "Failed", "Cancelled")

  protected def build_Program: ExecUowM[OperationResponse] =
    for {
      id <- exec_from(required_entity_id(action.record, "experimentRunId"))
      requested <- exec_from(action.record.getString("status")
        .map(Consequence.success)
        .getOrElse(Consequence.failRecordNotFound("status", action.record)))
      _ <- if (_terminal_statuses.contains(requested)) exec_pure(())
        else exec_from(Consequence.operationInvalid(
          s"Terminal experiment run status must be one of ${_terminal_statuses.toVector.sorted.mkString(", ")}."
        ))
      run <- entity_load_option_internal[ExperimentRun](id)
      current <- exec_from(run.map(Consequence.success).getOrElse(
        Consequence.entityNotFound(s"Experiment run '$id' does not exist.")
      ))
      evidence = action.record.getString("completionEvidenceReference")
      response <- current.status.value match {
        case "Running" =>
          val updated = current.copy(
            status = ExperimentRunStatus(requested),
            completionEvidenceReference = evidence.map(ExperimentEvidenceReference.apply)
          )
          for {
            patch <- exec_from(ExperimentRunUpdate.createC(Record.dataAuto(
              "status" -> requested,
              "completionEvidenceReference" -> evidence
            )))
            _ <- entity_update(current.id, patch)
          } yield _response(updated)
        case status if status == requested && current.completionEvidenceReference.map(_.value) == evidence =>
          exec_pure(_response(current))
        case status =>
          exec_from(Consequence.stateConflict(
            s"Experiment run in terminal status '$status' cannot be completed as '$requested'."
          ))
      }
    } yield response

  private def _response(run: ExperimentRun): OperationResponse =
    OperationResponse(Record.dataAuto("id" -> run.id.value, "status" -> run.status.value))
}

private final case class RecordObservationActionCallImpl(
  core: ActionCall.Core,
  override val action: ExperimentComponent.ExperimentManagementService.RecordObservation
) extends ExperimentComponent.ExperimentManagementService.RecordObservationActionCall
    with ExperimentActionSupport {
  private val _outcomes = Set("accepted", "repaired", "confirmed", "escalated", "rejected", "failed")

  protected def build_Program: ExecUowM[OperationResponse] =
    for {
      input <- exec_from(ExperimentObservationCreate.createC(action.record))
      _ <- if (_outcomes.contains(input.outcome.value)) exec_pure(())
        else exec_from(Consequence.operationInvalid(
          s"Observation outcome must be one of ${_outcomes.toVector.sorted.mkString(", ")}."
        ))
      run <- entity_load_option_internal[ExperimentRun](input.experimentRunId)
      currentrun <- exec_from(run.map(Consequence.success).getOrElse(
        Consequence.entityNotFound(s"Experiment run '${input.experimentRunId}' does not exist.")
      ))
      arm <- entity_load_option_internal[ExperimentArm](input.experimentArmId)
      currentarm <- exec_from(arm.map(Consequence.success).getOrElse(
        Consequence.entityNotFound(s"Experiment arm '${input.experimentArmId}' does not exist.")
      ))
      observations <- all_observations
      existing = observations.find(x =>
        x.experimentRunId == input.experimentRunId &&
          x.experimentArmId == input.experimentArmId &&
          x.corpusCaseId == input.corpusCaseId
      )
      response <- existing match {
        case Some(current) if _same(current, input) =>
          exec_pure(OperationResponse(Record.dataAuto("id" -> current.id.value)))
        case Some(_) =>
          exec_from(Consequence.stateConflict("The observation tuple is immutable and already has different content."))
        case None if currentrun.status.value != "Running" =>
          exec_from(Consequence.stateConflict("Observations can only be added to a Running experiment run."))
        case None if currentarm.experimentId != currentrun.experimentId =>
          exec_from(Consequence.stateConflict("Experiment arm does not belong to the experiment run."))
        case None =>
          entity_create(input).map(created => OperationResponse(Record.dataAuto("id" -> created.id.value)))
      }
    } yield response

  private def _same(current: ExperimentObservation, input: ExperimentObservationCreate): Boolean =
    current.outcome == input.outcome &&
      current.acceptanceEvidenceReference == input.acceptanceEvidenceReference &&
      current.executionEvidenceReference == input.executionEvidenceReference &&
      current.metricReference == input.metricReference
}

private final case class ListExperimentRunsActionCallImpl(
  core: ActionCall.Core,
  override val action: ExperimentComponent.ExperimentManagementService.ListExperimentRuns
) extends ExperimentComponent.ExperimentManagementService.ListExperimentRunsActionCall
    with ExperimentActionSupport {
  protected def build_Program: ExecUowM[OperationResponse] =
    for {
      runs <- all_runs
      experimentid = action.record.getAs[EntityId]("experimentId")
      selected = page(runs
        .filter(x => experimentid.forall(_ == x.experimentId))
        .filter(x => action.record.getString("status").forall(_ == x.status.value))
        .sortBy(_.runReference.value), action.record)
    } yield OperationResponse(Record.dataAuto("items" -> selected.map(run_record)))
}

private final case class ListExperimentObservationsActionCallImpl(
  core: ActionCall.Core,
  override val action: ExperimentComponent.ExperimentManagementService.ListExperimentObservations
) extends ExperimentComponent.ExperimentManagementService.ListExperimentObservationsActionCall
    with ExperimentActionSupport {
  protected def build_Program: ExecUowM[OperationResponse] =
    for {
      runid <- exec_from(required_entity_id(action.record, "experimentRunId"))
      observations <- all_observations
      armid = action.record.getAs[EntityId]("experimentArmId")
      selected = page(observations
        .filter(_.experimentRunId == runid)
        .filter(x => armid.forall(_ == x.experimentArmId))
        .filter(x => action.record.getString("outcome").forall(_ == x.outcome.value))
        .sortBy(x => (x.experimentArmId.value, x.corpusCaseId.value)), action.record)
    } yield OperationResponse(Record.dataAuto("items" -> selected.map(observation_record)))
}

private final case class SummarizeExperimentRunActionCallImpl(
  core: ActionCall.Core,
  override val action: ExperimentComponent.ExperimentManagementService.SummarizeExperimentRun
) extends ExperimentComponent.ExperimentManagementService.SummarizeExperimentRunActionCall
    with ExperimentActionSupport {
  protected def build_Program: ExecUowM[OperationResponse] =
    for {
      runid <- exec_from(required_entity_id(action.record, "experimentRunId"))
      run <- entity_load_option_internal[ExperimentRun](runid)
      _ <- exec_from(run.map(_ => Consequence.unit).getOrElse(
        Consequence.entityNotFound(s"Experiment run '$runid' does not exist.")
      ))
      observations <- all_observations
      selected = observations.filter(_.experimentRunId == runid)
      counts = selected.groupMapReduce(_.outcome.value)(_ => 1)(_ + _)
    } yield OperationResponse(Record.dataAuto(
      "experimentRunId" -> runid.value,
      "observationCount" -> selected.size,
      "acceptedCount" -> counts.getOrElse("accepted", 0),
      "repairedCount" -> counts.getOrElse("repaired", 0),
      "confirmedCount" -> counts.getOrElse("confirmed", 0),
      "escalatedCount" -> counts.getOrElse("escalated", 0),
      "rejectedCount" -> counts.getOrElse("rejected", 0),
      "failedCount" -> counts.getOrElse("failed", 0)
    ))
}

final class DefaultEntityServiceFactory extends ExperimentComponent.EntityServiceFactory

object DefaultEntityServiceFactory {
  def apply(): DefaultEntityServiceFactory = new DefaultEntityServiceFactory()
}

final class AggregateServiceFactoryImpl extends ExperimentComponent.AggregateServiceFactory

object AggregateServiceFactoryImpl {
  def apply(): AggregateServiceFactoryImpl = new AggregateServiceFactoryImpl()
}

final class ViewServiceFactoryImpl extends ExperimentComponent.ViewServiceFactory

object ViewServiceFactoryImpl {
  def apply(): ViewServiceFactoryImpl = new ViewServiceFactoryImpl()
}
