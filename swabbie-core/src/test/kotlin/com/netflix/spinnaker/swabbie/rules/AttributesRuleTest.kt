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
import com.netflix.spinnaker.swabbie.test.TestResource
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.netflix.spinnaker.config.ResourceTypeConfiguration.RuleDefinition
import com.netflix.spinnaker.swabbie.exclusions.LiteralExclusionPolicy
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isNotNull
import strikt.assertions.isNull
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

object AttributesRuleTest {
  private val clock = Clock.fixed(Instant.now(), ZoneOffset.UTC)
  private val objectMapper = ObjectMapper()
    .registerKotlinModule()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

  private val rule = AttributesRule(listOf(LiteralExclusionPolicy()), objectMapper)
  private val createdAt = clock.instant().minus(5, ChronoUnit.DAYS)
  private val resource = TestResource(resourceId = "1", createTs = createdAt.toEpochMilli())

  @Test
  fun `should not apply`() {
    // rule does not apply on no parameters
    expectThat(rule.apply(resource).summary).isNull()
    expectThat(rule.apply(resource, RuleDefinition()).summary).isNull()
  }

  /*
   * Rule definition
   * name: AttributesRule
   * parameters:
   *   name:
   *    - foo
   *    - pattern:^foo
  */
  @Test
  fun `should apply`() {
    val params = mapOf(
      "name" to listOf(
        "foo", "pattern:^some-name"
      )
    )

    val ruleDefinition = RuleDefinition()
      .apply {
        name = rule.name()
        parameters = params
      }

    expectThat(rule.apply(resource, ruleDefinition).summary).isNull()
    expectThat(rule.apply(resource.copy(name = "bar"), ruleDefinition).summary).isNull()
    expectThat(rule.apply(resource.copy(name = "foo"), ruleDefinition).summary).isNotNull()
    expectThat(rule.apply(resource.copy(name = "are-some-name-are-long"), ruleDefinition).summary).isNull() // contains some-name but doesn't start with it
    expectThat(rule.apply(resource.copy(name = "some-name-are-long"), ruleDefinition).summary).isNotNull()
  }
}
