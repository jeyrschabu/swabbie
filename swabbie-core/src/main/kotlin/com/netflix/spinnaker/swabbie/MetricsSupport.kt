/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.swabbie

import com.netflix.spectator.api.Id
import com.netflix.spectator.api.Registry
import com.netflix.spectator.api.patterns.LongTaskTimer
import com.netflix.spinnaker.swabbie.events.Action
import com.netflix.spinnaker.swabbie.model.WorkConfiguration
import java.util.concurrent.atomic.AtomicInteger

// TODO: Jeyrs - refactor me I hate this!!!!
open class MetricsSupport(
  private val registry: Registry
) {
  protected val markDurationTimer: LongTaskTimer = LongTaskTimer.get(
    registry, registry.createId("swabbie.resources.mark.duration")
  )

  protected val resourcesVisitedId: Id = registry.createId("swabbie.resources.visited")
  protected val noxtificationsId: Id = registry.createId("swabbie.resources.notifications")

  private val markViolationsId: Id = registry.createId("swabbie.resources.markViolations")
  private val resourcesExcludedId: Id = registry.createId("swabbie.resources.excluded")
  private val resourceFailureId: Id = registry.createId("swabbie.resources.failures")
  private val candidatesCountId: Id = registry.createId("swabbie.resources.candidatesCount")
  protected val totalMarkedId: Id = registry.createId("swabbie.resources.totalMarked")

  protected fun recordMarkMetrics(
    markerTimerId: Long,
    workConfiguration: WorkConfiguration,
    violationCounter: AtomicInteger,
    candidateCounter: AtomicInteger,
    totalResourcesVisitedCounter: AtomicInteger,
    totalMarkedCount: Long
  ) {
    markDurationTimer.stop(markerTimerId)
    registry.gauge(
      candidatesCountId.withTags(
        "resourceType", workConfiguration.resourceType,
        "configuration", workConfiguration.namespace,
        "resourceTypeHandler", javaClass.simpleName
      )).set(candidateCounter.toDouble())

    registry.gauge(
      resourcesVisitedId.withTags(
        "resourceType", workConfiguration.resourceType,
        "configuration", workConfiguration.namespace,
        "resourceTypeHandler", javaClass.simpleName
      )).set(totalResourcesVisitedCounter.toDouble())

    registry.gauge(totalMarkedId).set(totalMarkedCount.toDouble())
  }

  protected fun recordFailureForAction(action: Action, workConfiguration: WorkConfiguration, e: Exception) {
    registry.counter(
      resourceFailureId.withTags(
        "action", action.name,
        "resourceType", workConfiguration.resourceType,
        "configuration", workConfiguration.namespace,
        "resourceTypeHandler", javaClass.simpleName,
        "exception", e.javaClass.simpleName
      )).increment()
  }
}
