/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.swabbie.aws.volumes

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.config.SwabbieProperties
import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.swabbie.AbstractResourceTypeHandler
import com.netflix.spinnaker.swabbie.LockingService
import com.netflix.spinnaker.swabbie.Parameters
import com.netflix.spinnaker.swabbie.ResourceOwnerResolver
import com.netflix.spinnaker.swabbie.ResourceProvider
import com.netflix.spinnaker.swabbie.exclusions.ResourceExclusionPolicy
import com.netflix.spinnaker.swabbie.model.AWS
import com.netflix.spinnaker.swabbie.model.VOLUME
import com.netflix.spinnaker.swabbie.model.MarkedResource
import com.netflix.spinnaker.swabbie.model.NAIVE_EXCLUSION
import com.netflix.spinnaker.swabbie.model.Rule
import com.netflix.spinnaker.swabbie.model.WorkConfiguration
import com.netflix.spinnaker.swabbie.notifications.Notifier
import com.netflix.spinnaker.swabbie.orca.OrcaService
import com.netflix.spinnaker.swabbie.repository.ResourceStateRepository
import com.netflix.spinnaker.swabbie.repository.ResourceTrackingRepository
import com.netflix.spinnaker.swabbie.repository.ResourceUseTrackingRepository
import com.netflix.spinnaker.swabbie.repository.TaskTrackingRepository
import com.netflix.spinnaker.swabbie.utils.ApplicationUtils
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.time.Clock
import java.util.Optional
import kotlin.system.measureTimeMillis

@Component
class AmazonEbsVolumeHandler(
  registry: Registry,
  clock: Clock,
  notifiers: List<Notifier>,
  resourceTrackingRepository: ResourceTrackingRepository,
  resourceStateRepository: ResourceStateRepository,
  resourceOwnerResolver: ResourceOwnerResolver<AmazonVolume>,
  exclusionPolicies: List<ResourceExclusionPolicy>,
  applicationEventPublisher: ApplicationEventPublisher,
  lockingService: Optional<LockingService>,
  retrySupport: RetrySupport,
  dynamicConfigService: DynamicConfigService,
  private val rules: List<Rule<AmazonVolume>>,
  private val ebsVolumeProvider: ResourceProvider<AmazonVolume>,
  private val orcaService: OrcaService,
  private val applicationUtils: ApplicationUtils,
  private val taskTrackingRepository: TaskTrackingRepository,
  private val resourceUseTrackingRepository: ResourceUseTrackingRepository,
  private val swabbieProperties: SwabbieProperties
) : AbstractResourceTypeHandler<AmazonVolume>(
  registry,
  clock,
  rules,
  resourceTrackingRepository,
  resourceStateRepository,
  exclusionPolicies,
  resourceOwnerResolver,
  notifiers,
  applicationEventPublisher,
  lockingService,
  retrySupport,
  resourceUseTrackingRepository,
  swabbieProperties,
  dynamicConfigService
) {

  override fun deleteResources(markedResources: List<MarkedResource>, workConfiguration: WorkConfiguration) {
  }

  override fun handles(workConfiguration: WorkConfiguration): Boolean =
    workConfiguration.resourceType == VOLUME && workConfiguration.cloudProvider == AWS && !rules.isEmpty()

  override fun getCandidates(workConfiguration: WorkConfiguration): List<AmazonVolume>? {
    val params = Parameters(
      account = workConfiguration.account.accountId!!,
      region = workConfiguration.location,
      environment = workConfiguration.account.environment
    )

    return ebsVolumeProvider.getAll(params).also { images ->
      log.info("Got {} ebs volumes.", images?.size)
    }
  }

  override fun preProcessCandidates(
    candidates: List<AmazonVolume>,
    workConfiguration: WorkConfiguration
  ): List<AmazonVolume> {
    checkReferences(
      volumes = candidates,
      params = Parameters(
        account = workConfiguration.account.accountId!!,
        region = workConfiguration.location,
        environment = workConfiguration.account.environment
      )
    )

    return candidates
  }

  /**
   * Checks references for:
   * 1. Snapshots.
   * 2. Images.
   * 3. Seen in use recently.
   * Bubbles up any raised exception.
   */
  private fun checkReferences(volumes: List<AmazonVolume>?, params: Parameters) {
    if (volumes == null || volumes.isEmpty()) {
      return
    }

    log.debug("checking references for {} ebs volumes. Parameters: {}", volumes.size, params)

    val elapsedTimeMillis = measureTimeMillis {
      try {
        setAttached(volumes, params)
        setSeenWithinUnusedThreshold(volumes)
      } catch (e: Exception) {
        log.error("Failed to check volumes references. Params: {}", params, e)
        throw IllegalStateException("Unable to process ${volumes.size} images. Params: $params", e)
      }
    }

    log.info("Completed checking references for {} volumes in $elapsedTimeMillis ms. Params: {}", volumes.size, params)
  }

  /**
   * Checks if volumes are attached
   */
  private fun setAttached(
    volumes: List<AmazonVolume>,
    params: Parameters
  ) {
    volumes
      .filter { NAIVE_EXCLUSION !in it.details }
      .forEach { volume ->
        if (volume.state == "available") {
          if (!volume.snapshotId.isNullOrBlank()) {
            volume.set(USED_BY_SNAPSHOT, true)
            resourceUseTrackingRepository.recordUse(
              volume.resourceId,
              volume.snapshotId
            )
          }

          if (volume.attachments.isNotEmpty()) {
            volume.set(ATTACHED_TO_INSTANCES, true)
            resourceUseTrackingRepository.recordUse(
              volume.resourceId,
              volume.attachments.filter { it.state == "attached" }.map { it.instanceId }.joinToString { ", " }
            )
          }
        } else {
          log.debug("Volume {} in {} is not attached", volume.volumeId, params.region)
        }
      }
  }

  /**
   * Checks if an image has been seen in use recently.
   */
  private fun setSeenWithinUnusedThreshold(volumes: List<AmazonVolume>) {
    log.info("Checking for volumes that haven't been seen in more than ${swabbieProperties.outOfUseThresholdDays} days")
    if (swabbieProperties.outOfUseThresholdDays == 0) {
      log.info("Bypassing seen in use check, since `swabbieProperties.outOfUseThresholdDays` is 0")
      return
    }
    val used = resourceUseTrackingRepository.getUsed()
    val unusedAndTracked: Map<String, String> = resourceUseTrackingRepository
      .getUnused()
      .map {
        it.resourceId to it.usedByResourceId
      }.toMap()

    volumes.filter {
      NAIVE_EXCLUSION !in it.details &&
        USED_BY_SNAPSHOT !in it.details &&
        ATTACHED_TO_INSTANCES !in it.details
    }.forEach { volume ->
      if (!unusedAndTracked.containsKey(volume.volumeId) && used.contains(volume.volumeId)) {
        volume.set(SEEN_IN_USE_RECENTLY, true)
        log.debug("Volume {} has been SEEN_IN_USE_RECENTLY", volume.volumeId)
      }
    }
  }

  override fun getCandidate(
    resourceId: String,
    resourceName: String,
    workConfiguration: WorkConfiguration
  ): AmazonVolume? {
    val params = Parameters(
      id = resourceId,
      account = workConfiguration.account.accountId!!,
      region = workConfiguration.location,
      environment = workConfiguration.account.environment
    )

    return ebsVolumeProvider.getOne(params)
  }
}
