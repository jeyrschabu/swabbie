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

package com.netflix.spinnaker.swabbie.aws.launchconfigurations

import com.netflix.spinnaker.config.ResourceTypeConfiguration.RuleDefinition
import com.netflix.spinnaker.swabbie.model.Resource
import com.netflix.spinnaker.swabbie.model.Result
import com.netflix.spinnaker.swabbie.model.Rule
import com.netflix.spinnaker.swabbie.model.Summary
import org.springframework.stereotype.Component

@Component
class NoServerGroupRule : Rule {
  override fun <T : Resource> applicableForType(clazz: Class<T>): Boolean {
    return AmazonLaunchConfiguration::class.java.isAssignableFrom(clazz)
  }

  override fun <T : Resource> apply(resource: T, ruleDefinition: RuleDefinition?): Result {
    if (resource !is AmazonLaunchConfiguration || resource.details[isUsedByServerGroups] as? Boolean == true) {
      return Result(null)
    }

    return Result(
      Summary("Launch Configuration ${resource.resourceId} has no server groups", name())
    )
  }

  companion object {
    const val isUsedByServerGroups = "isUsedByServerGroups"
  }
}

@Component
class NoImageRule : Rule {
  override fun <T : Resource> applicableForType(clazz: Class<T>): Boolean {
    return AmazonLaunchConfiguration::class.java.isAssignableFrom(clazz)
  }

  override fun <T : Resource> apply(resource: T, ruleDefinition: RuleDefinition?): Result {
    if (resource !is AmazonLaunchConfiguration || resource.details[hasImage] as? Boolean == true) {
      return Result(null)
    }

    return Result(
      Summary("Launch Configuration ${resource.resourceId} 's image ${resource.imageId} does not exist.", name())
    )
  }

  companion object {
    const val hasImage = "hasImage"
  }
}
