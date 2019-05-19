/*
 *
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
 *
 */

package com.netflix.spinnaker.swabbie.aws.edda.providers

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.config.EddaApiClient
import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.spinnaker.swabbie.Parameters
import com.netflix.spinnaker.swabbie.ResourceProvider
import com.netflix.spinnaker.swabbie.aws.edda.EddaService
import com.netflix.spinnaker.swabbie.aws.volumes.AmazonVolume
import com.netflix.spinnaker.swabbie.model.SNAPSHOT
import com.netflix.spinnaker.swabbie.model.VOLUME
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import retrofit.RetrofitError

@Component
class EddaAmazonVolumeProvider(
  eddaApiClients: List<EddaApiClient>,
  private val retrySupport: RetrySupport,
  private val registry: Registry
) : ResourceProvider<AmazonVolume>, EddaApiSupport(eddaApiClients, registry) {
  private val log: Logger = LoggerFactory.getLogger(javaClass)

  override fun getAll(params: Parameters): List<AmazonVolume>? {
    withEddaClient(
      region = params.region,
      accountId = params.account,
      environment = params.environment
    )?.run {
      return getEbsVolumes()
    }

    return emptyList()
  }

  override fun getOne(params: Parameters): AmazonVolume? {
    withEddaClient(
      region = params.region,
      accountId = params.account,
      environment = params.environment
    )?.run {
      return getEbsVolume(params.id)
    }

    return null
  }

  private fun EddaService.getEbsVolumes(): List<AmazonVolume>? {
    return try {
      retrySupport.retry({
        getVolumes()
      }, maxRetries, retryBackOffMillis, true)
    } catch (e: Exception) {
      registry.counter(eddaFailureCountId.withTags("resourceType", SNAPSHOT)).increment()
      log.error("failed to get volumes", e)
      throw e
    }
  }

  private fun EddaService.getEbsVolume(volumeId: String): AmazonVolume? {
    return try {
      retrySupport.retry({
        try {
          this.getVolume(volumeId)
        } catch (e: Exception) {
          if (e is RetrofitError && e.response.status == 404) {
            null
          } else {
            throw e
          }
        }
      }, maxRetries, retryBackOffMillis, false)
    } catch (e: Exception) {
      registry.counter(eddaFailureCountId.withTags("resourceType", VOLUME)).increment()
      log.error("failed to get volume {}", volumeId, e)
      throw e
    }
  }
}
