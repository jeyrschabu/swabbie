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

package com.netflix.spinnaker.swabbie.events

import com.netflix.spectator.api.Id
import com.netflix.spectator.api.Registry
import com.netflix.spectator.api.patterns.PolledMeter
import com.netflix.spinnaker.swabbie.MetricsSupport
import com.netflix.spinnaker.swabbie.model.*
import com.netflix.spinnaker.swabbie.repository.ResourceStateRepository
import com.netflix.spinnaker.swabbie.repository.TaskCompleteEventInfo
import com.netflix.spinnaker.swabbie.repository.TaskTrackingRepository
import com.netflix.spinnaker.swabbie.tagging.ResourceTagger
import com.netflix.spinnaker.swabbie.tagging.TaggingService
import com.netflix.spinnaker.swabbie.tagging.UpsertImageTagsRequest
import com.netflix.spinnaker.swabbie.tagging.UpsertServerGroupTagsRequest
import com.netflix.spinnaker.swabbie.utils.ApplicationUtils
import net.logstash.logback.argument.StructuredArguments
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.time.Clock
import java.util.concurrent.atomic.AtomicInteger

@Component
class ResourceStateManager(
  private val resourceStateRepository: ResourceStateRepository,
  private val clock: Clock,
  private val registry: Registry,
  @Autowired(required = false) private val resourceTagger: ResourceTagger?,
  private val taggingService: TaggingService,
  private val taskTrackingRepository: TaskTrackingRepository,
  private val applicationUtils: ApplicationUtils
) {
  private val markCountId: Id = registry.createId("swabbie.resources.markCount")
  private val unMarkCountId: Id = registry.createId("swabbie.resources.unMarkCount")
  private val deleteCountId: Id = registry.createId("swabbie.resources.deleteCount")
  private val notifyCountId: Id = registry.createId("swabbie.resources.notifyCount")
  private val optOutCountId: Id = registry.createId("swabbie.resources.optOutCount")
  private val excludedCountId: Id = registry.createId("swabbie.resources.excludedCount")
  private  val orcaTaskFailureId: Id = registry.createId("swabbie.resources.orcaTaskFailureCount")

  private val log = LoggerFactory.getLogger(javaClass)

  @EventListener
  fun handleResourceExcludedEvents(event: ResourceExcludedEvent) {
    registry.gauge(
      excludedCountId.withTags("region", event.workConfiguration.location,
        "account", event.workConfiguration.account.name,
        "resourceType", event.workConfiguration.resourceType))
  }

  @EventListener
  fun handleEvents(event: Event<MarkedResource>) {
    var id: Id? = null
    var msg: String? = null
    var removeTag = false
    when (event) {
      is MarkResourceEvent -> {
        id = markCountId
        msg = "${event.identifiableResource.typeAndName()} scheduled to be cleaned up on " +
          "${event.identifiableResource.deletionDate(clock)}"
      }

      is UnMarkResourceEvent -> {
        id = unMarkCountId
        removeTag = true
        msg = "${event.identifiableResource.typeAndName()}. No longer a cleanup candidate"
      }

      is OwnerNotifiedEvent -> {
        id = notifyCountId
        removeTag = false
        msg = "Notified ${event.identifiableResource.notificationInfo?.recipient} about soon to be cleaned up " +
          event.identifiableResource.typeAndName()
      }

      is OptOutResourceEvent -> {
        id = optOutCountId
        removeTag = true
        msg = "${event.identifiableResource.typeAndName()}. Opted Out"
      }

      is DeleteResourceEvent -> {
        id = deleteCountId
        removeTag = true
        msg = "Removing tag for now deleted ${event.identifiableResource.typeAndName()}"
      }

      is OrcaTaskFailureEvent -> {
        id = orcaTaskFailureId
        removeTag = false
        msg = generateFailureMessage(event)
        // todo eb: do we want this tagged here?
      }

      else -> log.warn("Unknown event type: ${event.javaClass.simpleName}")
    }

    updateState(event)
    id?.let {
      registry.counter(
        it.withTags(
          "configuration", event.workConfiguration.namespace,
          "resourceType", event.workConfiguration.resourceType
        )
      ).increment()
    }

    if (resourceTagger != null && msg != null) {
      tag(resourceTagger, event as Event<MarkedResource>, msg, removeTag)
    }
  }

  fun generateFailureMessage(event: Event<MarkedResource>) =
    "Task failure for action ${event.action} on resource ${event.identifiableResource.typeAndName()}"

  private fun tag(tagger: ResourceTagger, event: Event<MarkedResource>, msg: String, remove: Boolean = false) {
    if (!remove) {
      tagger.tag(
        markedResource = event.identifiableResource,
        workConfiguration = event.workConfiguration,
        description = msg
      )
    } else {
      tagger.unTag(
        markedResource = event.identifiableResource,
        workConfiguration = event.workConfiguration,
        description = msg
      )
    }
  }

  private fun updateState(event: Event<MarkedResource>) {
    val currentState = resourceStateRepository.get(
      resourceId = event.identifiableResource.resourceId,
      namespace = event.identifiableResource.namespace
    )
    val statusName = if (event is OrcaTaskFailureEvent) "${event.action.name} FAILED" else event.action.name
    val status = Status(statusName, clock.instant().toEpochMilli())

    currentState?.statuses?.add(status)
    val newState = (currentState?.copy(
      statuses = currentState.statuses,
      markedResource = event.identifiableResource,
      deleted = event is DeleteResourceEvent,
      optedOut = event is OptOutResourceEvent,
      currentStatus = status
    ) ?: ResourceState(
      markedResource = event.identifiableResource,
      deleted = event is DeleteResourceEvent,
      optedOut = event is OptOutResourceEvent,
      statuses = mutableListOf(status),
      currentStatus = status
    ))

    resourceStateRepository.upsert(newState)

    if (event is OptOutResourceEvent) {
      tagResource(event.identifiableResource, event.workConfiguration)
    }
  }

  // todo eb: pull to another kind of ResourceTagger?
  // todo aravind : handle snapshots tagging
  private fun tagResource(
    resource: MarkedResource,
    workConfiguration: WorkConfiguration
  ) {
    log.debug("Tagging resource ${resource.uniqueId()} with \"expiration_time\":\"never\"")
    val taskId = when (resource.resourceType) {
        "serverGroup" -> tagAsg(resource, workConfiguration)
        "image" -> tagImage(resource)
        else -> {
          log.error("Cannot tag resource type ${resource.resourceType} with an infrastructure tag ")
          null
        }
      }
    if (taskId != null) {
      taskTrackingRepository.add(
        taskId,
        TaskCompleteEventInfo(
          action = Action.OPTOUT,
          markedResources = listOf(resource),
          workConfiguration = workConfiguration,
          submittedTimeMillis = clock.instant().toEpochMilli()
        )
      )
      log.debug("Tagging resource ${resource.uniqueId()} in {}", StructuredArguments.kv("taskId", taskId))
    }
  }

  private fun tagAsg(resource: MarkedResource, workConfiguration: WorkConfiguration): String {
    return taggingService.upsertAsgTag(
      UpsertServerGroupTagsRequest(
        serverGroupName = resource.resourceId,
        regions = setOf(SwabbieNamespace.namespaceParser(resource.namespace).region),
        tags = mapOf("expiration_time" to "never"),
        cloudProvider = "aws",
        cloudProviderType = "aws",
        credentials = workConfiguration.account.name.toString(),
        application = applicationUtils.determineApp(resource.resource),
        description = "Setting `expiration_time` to `never` for serverGroup ${resource.uniqueId()}"
      )
    )
  }

  private fun tagImage(resource: MarkedResource): String {
    return taggingService.upsertImageTag(
      UpsertImageTagsRequest(
        imageNames = setOf(resource.name ?: resource.resourceId),
        regions = setOf(SwabbieNamespace.namespaceParser(resource.namespace).region),
        tags = mapOf("expiration_time" to "never"),
        cloudProvider = "aws",
        cloudProviderType = "aws",
        application = applicationUtils.determineApp(resource.resource),
        description = "Setting `expiration_time` to `never` for image ${resource.uniqueId()}"
      )
    )
  }
}

internal fun MarkedResource.typeAndName(): String {
  if (name == null || name == resourceId) {
    resourceId
  } else {
    "($resourceId) $name"
  }.let { suffix ->
    return resourceType
      .split("(?=[A-Z])".toRegex())
      .joinToString(" ") + ": $suffix"
  }
}

internal fun String.formatted(): String =
  this.split("(?=[A-Z])".toRegex()).joinToString(" ").toLowerCase()
