package io.prediction.engines.base

import io.prediction.controller.LPreparator
import io.prediction.controller.Params

//import io.prediction.engines.base.RatingTD
//import io.prediction.engines.base.TrainingData
//import io.prediction.engines.base.PreparedData

abstract class AbstractPreparatorParams extends Params {
  // how to map selected actions into rating value
  // use None if use U2IActionTD.v field
  val actions: Map[String, Option[Int]] // ((view, 1), (rate, None))
  val seenActions: Set[String] // (view, conversion)
  val conflict: String // conflict resolution, "latest" "highest" "lowest"
}

class Preparator(pp: AbstractPreparatorParams) extends LPreparator[
    AbstractPreparatorParams, TrainingData, PreparedData] {

  final val CONFLICT_LATEST: String = "latest"
  final val CONFLICT_HIGHEST: String = "highest"
  final val CONFLICT_LOWEST: String = "lowest"

  override def prepare(trainingData: TrainingData): PreparedData = {

    val actionsMap = pp.actions
    val conflict = pp.conflict

    // convert actions to ratings value
    val u2iRatings = trainingData.u2iActions
      .filter { u2i =>
        val validAction = actionsMap.contains(u2i.action)
        validAction
      }.map { u2i =>
        val rating = actionsMap(u2i.action).getOrElse(u2i.v.getOrElse(0))

        new RatingTD(
          uindex = u2i.uindex,
          iindex = u2i.iindex,
          rating = rating,
          t = u2i.t
        )
      }

    // resolve conflicts if users has rated items multiple times
    val ratingReduced = u2iRatings.groupBy(x => (x.iindex, x.uindex))
      .mapValues { v =>
        v.reduce { (a, b) =>
          resolveConflict(a, b, conflict)
        }
      }.values
      .toList

    new PreparedData(
      users = trainingData.users,
      items = trainingData.items,
      rating = ratingReduced,
      ratingOriginal = u2iRatings,
      seenU2IActions = Seq() // TODO: filter seen U2I
    )
  }

  private def resolveConflict(a: RatingTD, b: RatingTD,
    conflictParam: String) = {
    conflictParam match {
      case CONFLICT_LATEST  => if (a.t > b.t) a else b
      case CONFLICT_HIGHEST => if (a.rating > b.rating) a else b
      case CONFLICT_LOWEST  => if (a.rating < b.rating) a else b
    }
  }
}
