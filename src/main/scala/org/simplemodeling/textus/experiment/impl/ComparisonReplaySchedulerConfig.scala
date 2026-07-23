package org.simplemodeling.textus.experiment.impl

import org.goldenport.Consequence
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.configuration.ResolvedConfiguration

/*
 * Operator-owned limits for durable comparison replay reservations.
 *
 * @since   Jul. 23, 2026
 * @version Jul. 23, 2026
 * @author  ASAMI, Tomoharu
 */
private final case class ComparisonReplaySchedulerConfig(
  maximumReplayCount: Int,
  maximumBudgetMicrounits: Long,
  reservationTtlSeconds: Long
)

private object ComparisonReplaySchedulerConfig {
  private val _prefix = "textus.experiment.comparison-replay"

  def fromConfiguration(
    configuration: ResolvedConfiguration
  ): Consequence[ComparisonReplaySchedulerConfig] =
    _string(configuration, "enabled").map(_.toLowerCase(java.util.Locale.ROOT)) match {
      case Some("true") =>
        for {
          count <- _positive_int(configuration, "maximum-replay-count")
          budget <- _positive_long(configuration, "maximum-budget-microunits")
          ttl <- _non_negative_long(configuration, "reservation-ttl-seconds")
        } yield ComparisonReplaySchedulerConfig(count, budget, ttl)
      case Some("false") | None =>
        Consequence.configurationInvalid(s"$_prefix.enabled=true is required")
      case Some(_) =>
        Consequence.configurationInvalid(s"$_prefix.enabled must be true or false")
    }

  private def _positive_int(
    configuration: ResolvedConfiguration,
    suffix: String
  ): Consequence[Int] =
    _string(configuration, suffix).flatMap(_.toIntOption).filter(_ > 0)
      .map(Consequence.success)
      .getOrElse(Consequence.configurationInvalid(s"$_prefix.$suffix must be a positive integer"))

  private def _positive_long(
    configuration: ResolvedConfiguration,
    suffix: String
  ): Consequence[Long] =
    _string(configuration, suffix).flatMap(_.toLongOption).filter(_ > 0)
      .map(Consequence.success)
      .getOrElse(Consequence.configurationInvalid(s"$_prefix.$suffix must be a positive long"))

  private def _non_negative_long(
    configuration: ResolvedConfiguration,
    suffix: String
  ): Consequence[Long] =
    _string(configuration, suffix).flatMap(_.toLongOption).filter(_ >= 0)
      .map(Consequence.success)
      .getOrElse(Consequence.configurationInvalid(s"$_prefix.$suffix must be a non-negative long"))

  private def _string(configuration: ResolvedConfiguration, suffix: String): Option[String] =
    RuntimeConfig.getString(configuration, s"$_prefix.$suffix").map(_.trim).filter(_.nonEmpty)
}
