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

import com.netflix.spinnaker.swabbie.model.Result
import com.netflix.spinnaker.swabbie.model.Rule
import com.netflix.spinnaker.swabbie.model.Summary
import org.springframework.stereotype.Component

// TODO: update header
/**
 * Images are marked when they are orphaned.
 *
 * The `outOfUseThresholdDays` property controls the amount of time
 *  we let a resource be unseen (out of use) before it is marked and deleted.
 * For example, if `outOfUseThresholdDays = 10`, then an image is allowed to sit in
 *  orphaned state (defined by the rules below) for 10 days before it will be marked.
 */
@Component
class OrphanedVolumeRule : Rule<AmazonVolume> {
  override fun apply(resource: AmazonVolume): Result {
    if (resource.matchesAnyRule(
        ATTACHED_TO_INSTANCES,
        USED_BY_SNAPSHOT,
        SEEN_IN_USE_RECENTLY
      )) {
      return Result(null)
    }

    return Result(
      Summary(
        description = "Volume is not attached to an instance and is not used by a snapshot.",
        ruleName = name()
      )
    )
  }

  private fun AmazonVolume.matchesAnyRule(vararg ruleName: String): Boolean {
    return ruleName.any { details.containsKey(it) && details[it] as Boolean }
  }
}

const val ATTACHED_TO_INSTANCES = "attachedToInstances"
const val USED_BY_SNAPSHOT = "usedBySnapshot"
const val SEEN_IN_USE_RECENTLY = "seenInUseRecently"
