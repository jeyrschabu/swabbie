package com.netflix.spinnaker.handlers

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.swabbie.AbstractResourceTypeHandler
import com.netflix.spinnaker.swabbie.LockingService
import com.netflix.spinnaker.swabbie.ResourceOwnerResolver
import com.netflix.spinnaker.swabbie.ResourceTrackingRepository
import com.netflix.spinnaker.swabbie.echo.Notifier
import com.netflix.spinnaker.swabbie.exclusions.ResourceExclusionPolicy
import com.netflix.spinnaker.swabbie.model.MarkedResource
import com.netflix.spinnaker.swabbie.model.RemoteResource
import com.netflix.spinnaker.swabbie.model.Rule
import com.netflix.spinnaker.swabbie.model.WorkConfiguration
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.time.Clock
import java.util.*

@Component
class RemoteResourceHandler(
  registry: Registry,
  clock: Clock,
  notifier: Notifier,
  rules: List<Rule<RemoteResource>>,
  resourceTrackingRepository: ResourceTrackingRepository,
  resourceOwnerResolver: ResourceOwnerResolver<RemoteResource>,
  exclusionPolicies: List<ResourceExclusionPolicy>,
  applicationEventPublisher: ApplicationEventPublisher,
  lockingService: Optional<LockingService>
) : AbstractResourceTypeHandler<RemoteResource>(
  registry,
  clock,
  rules,
  resourceTrackingRepository,
  exclusionPolicies,
  resourceOwnerResolver,
  notifier,
  applicationEventPublisher,
  lockingService
) {
  override fun remove(markedResource: MarkedResource, workConfiguration: WorkConfiguration) {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun handles(workConfiguration: WorkConfiguration): Boolean {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun getUpstreamResources(workConfiguration: WorkConfiguration): List<RemoteResource>? {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun getUpstreamResource(markedResource: MarkedResource, workConfiguration: WorkConfiguration): RemoteResource? {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

}
