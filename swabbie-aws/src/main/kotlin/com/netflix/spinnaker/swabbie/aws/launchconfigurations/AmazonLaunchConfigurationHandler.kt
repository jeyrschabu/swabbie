/*
 *
 *  Copyright 2019 Netflix, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License")
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.netflix.spinnaker.swabbie.aws.launchconfigurations

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.config.SwabbieProperties
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.swabbie.AbstractResourceTypeHandler
import com.netflix.spinnaker.swabbie.InMemorySingletonCache
import com.netflix.spinnaker.swabbie.ResourceOwnerResolver
import com.netflix.spinnaker.swabbie.aws.AWS
import com.netflix.spinnaker.swabbie.aws.Parameters
import com.netflix.spinnaker.swabbie.aws.caches.AmazonLaunchConfigurationCache
import com.netflix.spinnaker.swabbie.exclusions.ResourceExclusionPolicy
import com.netflix.spinnaker.swabbie.model.AWS
import com.netflix.spinnaker.swabbie.model.LAUNCH_CONFIGURATION
import com.netflix.spinnaker.swabbie.model.MarkedResource
import com.netflix.spinnaker.swabbie.model.WorkConfiguration
import com.netflix.spinnaker.swabbie.notifications.NotificationQueue
import com.netflix.spinnaker.swabbie.notifications.Notifier
import com.netflix.spinnaker.swabbie.orca.OrcaService
import com.netflix.spinnaker.swabbie.repository.ResourceStateRepository
import com.netflix.spinnaker.swabbie.repository.ResourceTrackingRepository
import com.netflix.spinnaker.swabbie.repository.ResourceUseTrackingRepository
import com.netflix.spinnaker.swabbie.repository.TaskTrackingRepository
import com.netflix.spinnaker.swabbie.rules.RulesEngine
import com.netflix.spinnaker.swabbie.utils.ApplicationUtils
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.time.Clock
import kotlin.system.measureTimeMillis

@Component
class AmazonLaunchConfigurationHandler(
  registry: Registry,
  clock: Clock,
  notifier: Notifier,
  resourceTrackingRepository: ResourceTrackingRepository,
  resourceStateRepository: ResourceStateRepository,
  resourceOwnerResolver: ResourceOwnerResolver<AmazonLaunchConfiguration>,
  exclusionPolicies: List<ResourceExclusionPolicy>,
  applicationEventPublisher: ApplicationEventPublisher,
  swabbieProperties: SwabbieProperties,
  dynamicConfigService: DynamicConfigService,
  private val launchConfigurationCache: InMemorySingletonCache<AmazonLaunchConfigurationCache>,
  private val rulesEngine: RulesEngine,
  private val aws: AWS,
  private val orcaService: OrcaService,
  private val applicationUtils: ApplicationUtils,
  private val taskTrackingRepository: TaskTrackingRepository,
  private val resourceUseTrackingRepository: ResourceUseTrackingRepository,
  notificationQueue: NotificationQueue
) : AbstractResourceTypeHandler<AmazonLaunchConfiguration>(
  registry,
  clock,
  rulesEngine,
  resourceTrackingRepository,
  resourceStateRepository,
  exclusionPolicies,
  resourceOwnerResolver,
  notifier,
  applicationEventPublisher,
  resourceUseTrackingRepository,
  swabbieProperties,
  dynamicConfigService,
  notificationQueue
) {
  override fun deleteResources(markedResources: List<MarkedResource>, workConfiguration: WorkConfiguration) {
    TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
  }

  override fun handles(workConfiguration: WorkConfiguration): Boolean {
    return workConfiguration.resourceType == LAUNCH_CONFIGURATION && workConfiguration.cloudProvider == AWS &&
      rulesEngine.getRules(workConfiguration).isNotEmpty()
  }

  override fun getCandidates(workConfiguration: WorkConfiguration): List<AmazonLaunchConfiguration>? {
    val params = Parameters(
      account = workConfiguration.account.accountId!!,
      region = workConfiguration.location,
      environment = workConfiguration.account.environment
    )

    return aws.getLaunchConfigurations(params)
  }

  override fun getCandidate(
    resourceId: String,
    resourceName: String,
    workConfiguration: WorkConfiguration
  ): AmazonLaunchConfiguration? {
    val params = Parameters(
      id = resourceId,
      account = workConfiguration.account.accountId!!,
      region = workConfiguration.location,
      environment = workConfiguration.account.environment
    )

    return aws.getLaunchConfiguration(params)
  }

  override fun preProcessCandidates(
    candidates: List<AmazonLaunchConfiguration>,
    workConfiguration: WorkConfiguration
  ): List<AmazonLaunchConfiguration> {
    return candidates
      .also {
        checkReferences(
          launchConfigurations = it,
          params = Parameters(
            account = workConfiguration.account.accountId!!,
            region = workConfiguration.location,
            environment = workConfiguration.account.environment
          )
        )
      }
  }

  /**
   * Checks references for:
   * Server Groups
   * Images
   */
  private fun checkReferences(launchConfigurations: List<AmazonLaunchConfiguration>?, params: Parameters) {
    if (launchConfigurations.isNullOrEmpty()) {
      return
    }

    log.debug("checking references for {} launch configs. Parameters: {}", launchConfigurations.size, params)
    val elapsedTimeMillis = measureTimeMillis {
      setServerGroupAndImageReferences(launchConfigurations, params)
    }

    log.info("Completed checking references for {} launch configs in $elapsedTimeMillis ms. Params: {}",
      launchConfigurations.size, params)
  }

  private fun setServerGroupAndImageReferences(launchConfigurations: List<AmazonLaunchConfiguration>, params: Parameters) {
    val usedByServerGroups = aws
      .getServerGroups(params)
      .map {
        it.launchConfigurationName
      }

    val launchConfigs = launchConfigurationCache.get()
    launchConfigurations.forEach { lc ->
      val refByImages = launchConfigs.getLaunchConfigsByRegionForImage(params.copy(id = lc.resourceId))
      lc.set(NoServerGroupRule.isUsedByServerGroups, lc.name in usedByServerGroups)
      lc.set(NoImageRule.hasImage, refByImages.isNotEmpty())
    }
  }
}
