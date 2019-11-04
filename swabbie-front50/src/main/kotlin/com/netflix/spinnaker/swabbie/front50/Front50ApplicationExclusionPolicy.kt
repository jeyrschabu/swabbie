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

package com.netflix.spinnaker.swabbie.front50

import com.netflix.spinnaker.config.Exclusion
import com.netflix.spinnaker.config.ExclusionType
import com.netflix.spinnaker.swabbie.InMemoryCache
import com.netflix.spinnaker.swabbie.exclusions.Excludable
import com.netflix.spinnaker.swabbie.exclusions.ResourceExclusionPolicy
import com.netflix.spinnaker.swabbie.model.Application
import com.netflix.spinnaker.swabbie.model.Grouping
import com.netflix.spinnaker.swabbie.model.GroupingType
import org.springframework.stereotype.Component

/**
 * todo eb: for images, we could find applications they belong to by seeing what images are running in each:
 *  find the package name of the image by using Frigga AppVersion,
 *  then build a cache of packageVersion to listOf(appName).
 *  Also build a cache of appName to owner.
 *  This would let us accurately determine which packages are in use by each app by querying both caches
 */
@Component
class Front50ApplicationExclusionPolicy(
  private val front50ApplicationCache: InMemoryCache<Application>
) : ResourceExclusionPolicy {

  private fun findApplication(excludable: Excludable, names: Set<String>): Excludable? {
    val grouping: Grouping = excludable.grouping ?: return null
    if (grouping.type == GroupingType.APPLICATION) {
      return front50ApplicationCache.get().find { matchesApplication(it, grouping.value, names) }
    }
    return null
  }

  private fun matchesApplication(application: Application, name: String, names: Set<String>): Boolean {
    return application.name.equals(name, ignoreCase = true) ||
      names.any { it.equals(application.name, ignoreCase = true) || application.name.matchPattern(it) }
  }

  override fun getType(): ExclusionType = ExclusionType.Application

  override fun apply(excludable: Excludable, exclusions: List<Exclusion>): String? {
    val kv = keysAndValues(exclusions, ExclusionType.Allowlist)
    val names: List<String> = kv.values.flatten().map { it.toString() }
    val application = findApplication(excludable, names.toSet())

    if (application != null) {
      return byPropertyMatchingResult(exclusions, application)
    }

    return null
  }
}
