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

package com.netflix.spinnaker.swabbie.rules

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.config.Attribute
import com.netflix.spinnaker.config.Exclusion
import com.netflix.spinnaker.config.ExclusionType.Literal
import com.netflix.spinnaker.config.ResourceTypeConfiguration.RuleDefinition
import com.netflix.spinnaker.swabbie.exclusions.ExclusionPolicy
import com.netflix.spinnaker.swabbie.model.Result
import com.netflix.spinnaker.swabbie.model.Summary
import com.netflix.spinnaker.swabbie.model.Resource
import com.netflix.spinnaker.swabbie.model.Rule
import org.springframework.stereotype.Component

@Component
class AttributesRule(
  val exclusionPolicies: List<ExclusionPolicy>,
  val objectMapper: ObjectMapper
) : Rule {
  override fun <T : Resource> applicableForType(clazz: Class<T>): Boolean {
    return Resource::class.java.isAssignableFrom(clazz)
  }
  /**
   * @param ruleDefinition parameters example:
   * parameters:
   *  - name:
   *    - foo
   *    - pattern:^foo
   */
  override fun <T : Resource> apply(resource: T, ruleDefinition: RuleDefinition?): Result {
    val params = ruleDefinition?.parameters ?: return Result(null)
    if (params.isEmpty()) {
      return Result(null)
    }

    if (!resource.attributeMatch(params)) {
      return Result(null)
    }

    return Result(Summary(description = "(${resource.resourceId}): matched by rule attributes.", ruleName = name()))
  }

  private fun <T : Resource> T.attributeMatch(attrs: Map<String, Any>): Boolean {
    val attributes = attrs.map {
      Attribute()
        .withKey(it.key)
        .withValue(it.value as List<Any>)
    }

    val exclusions = attributes.map {
      Exclusion()
        .withType(Literal.name)
        .withAttributes(attributes.toSet())
    }

    return shouldBeExcluded(exclusionPolicies, exclusions).excluded
  }
}
