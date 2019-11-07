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

package com.netflix.spinnaker.swabbie.events

import com.netflix.spinnaker.swabbie.model.Identifiable
import com.netflix.spinnaker.swabbie.model.MarkedResource
import com.netflix.spinnaker.swabbie.model.Resource
import com.netflix.spinnaker.swabbie.model.WorkConfiguration

abstract class Event<T : Identifiable>(
  open val action: Action,
  open val identifiableResource: T,
  open val workConfiguration: WorkConfiguration
) {
  override fun equals(other: Any?): Boolean {
    if (other is Event<*>) {
      return action == other && other.identifiableResource == identifiableResource &&
        workConfiguration == other.workConfiguration
    }
    return super.equals(other)
  }

  override fun hashCode(): Int {
    var result = action.name.hashCode()
    result = 31 * result + identifiableResource.hashCode()
    result = 31 * result + workConfiguration.hashCode()
    return result
  }
}

enum class Action {
  MARK, UNMARK, DELETE, NOTIFY, OPTOUT, EXCLUDE
}

class OwnerNotifiedEvent(
  override val identifiableResource: MarkedResource,
  override val workConfiguration: WorkConfiguration
) : Event<MarkedResource>(Action.NOTIFY, identifiableResource, workConfiguration)

class UnMarkResourceEvent(
  override val identifiableResource: MarkedResource,
  override val workConfiguration: WorkConfiguration
) : Event<MarkedResource>(Action.UNMARK, identifiableResource, workConfiguration)

class MarkResourceEvent(
  override val identifiableResource: MarkedResource,
  override val workConfiguration: WorkConfiguration
) : Event<MarkedResource>(Action.MARK, identifiableResource, workConfiguration)

class DeleteResourceEvent(
  override val identifiableResource: MarkedResource,
  override val workConfiguration: WorkConfiguration
) : Event<MarkedResource>(Action.DELETE, identifiableResource, workConfiguration)

class OptOutResourceEvent(
  override val identifiableResource: MarkedResource,
  override val workConfiguration: WorkConfiguration
) : Event<MarkedResource>(Action.OPTOUT, identifiableResource, workConfiguration)

class ResourceExcludedEvent(
  override val identifiableResource: Resource,
  override val workConfiguration: WorkConfiguration
) : Event<Resource>(Action.EXCLUDE, identifiableResource, workConfiguration)

class OrcaTaskFailureEvent(
  override val action: Action,
  override val identifiableResource: MarkedResource,
  override val workConfiguration: WorkConfiguration
) : Event<MarkedResource>(action, identifiableResource, workConfiguration)
