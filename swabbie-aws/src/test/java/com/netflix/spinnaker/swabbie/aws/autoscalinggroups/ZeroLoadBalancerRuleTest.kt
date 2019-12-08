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

package com.netflix.spinnaker.swabbie.aws.autoscalinggroups

import org.junit.Test
import strikt.api.expectThat
import strikt.assertions.isNotNull
import strikt.assertions.isNull
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

object ZeroLoadBalancerRuleTest {
  private val clock = Clock.fixed(Instant.now(), ZoneOffset.UTC)

  @Test
  fun `should apply if server group has no load balancer`() {
    val rule = ZeroLoadBalancerRule()
    val asg = AmazonAutoScalingGroup(
      autoScalingGroupName = "testapp-v001",
      instances = listOf(
        mapOf("instanceId" to "i-01234")
      ),
      loadBalancerNames = listOf(),
      createdTime = clock.millis(),
      launchConfigurationName = "testapp-v001-1"
    )

    expectThat(rule.apply(asg).summary).isNotNull()

    expectThat(rule.apply(asg.copy(loadBalancerNames = listOf("lb"))).summary).isNull()
  }
}
