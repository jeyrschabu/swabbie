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

package com.netflix.spinnaker.swabbie.aws.volumes

import com.fasterxml.jackson.annotation.JsonTypeName
import com.netflix.spinnaker.swabbie.aws.model.AmazonResource
import com.netflix.spinnaker.swabbie.model.AWS
import com.netflix.spinnaker.swabbie.model.VOLUME
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@JsonTypeName("amazonVolume")
data class AmazonVolume(
  val volumeId: String,
  val state: String,
  val snapshotId: String?,
  val attachments: List<Attachment>,
  override val resourceId: String = volumeId,
  override val resourceType: String = VOLUME,
  override val cloudProvider: String = AWS,
  override val name: String = volumeId,
  private val createdTime: Long,
  private val creationDate: String? =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(createdTime), ZoneId.systemDefault()).toString()
) : AmazonResource(creationDate) {
  override fun equals(other: Any?): Boolean {
    return super.equals(other)
  }

  override fun hashCode(): Int {
    return super.hashCode()
  }
}

data class Attachment(
  val attachTime: Long,
  val volumeId: String,
  val state: String,
  val instanceId: String,
  val device: String,
  val deleteOnTermination: Boolean
)
