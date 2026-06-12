/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.syncope.core.provisioning.java.pushpull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.syncope.common.lib.to.Provision;
import org.apache.syncope.common.lib.to.ProvisioningReport;
import org.apache.syncope.common.lib.types.AnyTypeKind;
import org.apache.syncope.common.lib.types.ResourceOperation;
import org.apache.syncope.core.persistence.api.dao.GroupDAO;
import org.apache.syncope.core.persistence.api.dao.UserDAO;
import org.apache.syncope.core.persistence.api.entity.Any;
import org.apache.syncope.core.persistence.api.entity.Entity;
import org.apache.syncope.core.persistence.api.entity.ExternalResource;
import org.apache.syncope.core.persistence.api.entity.group.Group;
import org.apache.syncope.core.persistence.api.entity.user.User;
import org.apache.syncope.core.provisioning.api.MappingManager;
import org.apache.syncope.core.provisioning.api.pushpull.ProvisioningProfile;
import org.apache.syncope.core.provisioning.api.pushpull.PushActions;
import org.apache.syncope.core.spring.implementation.InstanceScope;
import org.apache.syncope.core.spring.implementation.SyncopeImplementation;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.SearchResult;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.spi.SearchResultsHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * PushActions implementation that deletes downstream REST objects which no longer exist in Syncope.
 *
 * After a Push task completes, queries the downstream connector for ALL objects (via the
 * connector's search script). Any downstream object whose UID does not match any Syncope
 * entity's computed connector-object-key is considered an orphan and deleted from the downstream
 * (via the connector's delete script).
 *
 * <p>Direction: downstream has object, Syncope does NOT → delete from downstream.
 */
@SyncopeImplementation(scope = InstanceScope.PER_CONTEXT)
public class RestOrphanCleanupPushActions implements PushActions {

    private static final Logger LOG = LoggerFactory.getLogger(RestOrphanCleanupPushActions.class);

    @Autowired
    private UserDAO userDAO;

    @Autowired
    private GroupDAO groupDAO;

    @Autowired
    private MappingManager mappingManager;

    @Override
    public void onError(
            final ProvisioningProfile<?, ?> profile,
            final Entity entity,
            final ProvisioningReport result,
            final Exception error) {
        LOG.warn("Push action error for entity={} key={}: {}",
                entity == null ? null : entity.getClass().getSimpleName(),
                entity == null ? null : entity.getKey(),
                error == null ? null : error.getMessage());
    }

    // -------------------------------------------------------------------------
    // afterAll() — search downstream, compare with Syncope, delete extras
    // -------------------------------------------------------------------------

    @Override
    public void afterAll(final ProvisioningProfile<?, ?> profile) {
        if (profile.isDryRun()) {
            LOG.info("[DRY-RUN] Skip downstream orphan cleanup");
            return;
        }

        try {
            ExternalResource resource = profile.getTask().getResource();
            String resourceKey = resource.getKey();
            LOG.info("Downstream orphan cleanup started, resource={}", resourceKey);

            try {
                resource.getProvisionByAnyType(AnyTypeKind.USER.name()).ifPresentOrElse(userProvision -> {
                    List<String> usernames = expectedDownstreamIdentifiers(
                            profile, resource, AnyTypeKind.USER, userProvision);
                    LOG.info("This push processed users: {}", String.join(", ", usernames));
                    cleanDownstream(profile, usernames, AnyTypeKind.USER);
                }, () -> LOG.debug(
                        "No USER provision found for resource {}, skipping USER cleanup", resourceKey));
            } catch (Exception e) {
                LOG.error("Downstream USER orphan cleanup failed, skipping", e);
            }

            try {
                resource.getProvisionByAnyType(AnyTypeKind.GROUP.name()).ifPresentOrElse(groupProvision -> {
                    List<String> groupnames = expectedDownstreamIdentifiers(
                            profile, resource, AnyTypeKind.GROUP, groupProvision);
                    LOG.info("This push processed groups: {}", String.join(", ", groupnames));
                    cleanDownstream(profile, groupnames, AnyTypeKind.GROUP);
                }, () -> LOG.debug(
                        "No GROUP provision found for resource {}, skipping GROUP cleanup", resourceKey));
            } catch (Exception e) {
                LOG.error("Downstream GROUP orphan cleanup failed, skipping", e);
            }
        } catch (Exception e) {
            LOG.error("Downstream orphan cleanup failed before processing", e);
        }
    }

    // -------------------------------------------------------------------------
    // Internal: search downstream, build Syncope UID set, delete extras
    // -------------------------------------------------------------------------

    private void cleanDownstream(
            final ProvisioningProfile<?, ?> profile,
            final List<String> names,
            final AnyTypeKind anyTypeKind) {

        ObjectClass objectClass = anyTypeKind == AnyTypeKind.USER ? ObjectClass.ACCOUNT : ObjectClass.GROUP;

        // Step 1: collect ALL names currently in downstream (calls the connector's search script)
        List<String> downstreamUIDs = new ArrayList<>();
        profile.getConnector().search(objectClass, null, new SearchResultsHandler() {

            @Override
            public void handleResult(final SearchResult result) {
                // nothing to do
            }

            @Override
            public boolean handle(final ConnectorObject object) {
                Attribute nameAttr = object.getAttributeByName("name");
                String value = (nameAttr != null && !nameAttr.getValue().isEmpty())
                    ? nameAttr.getValue().get(0).toString()
                    : object.getUid().getUidValue();
                downstreamUIDs.add(value);
                return true;
            }
        }, new OperationOptionsBuilder().setAttributesToGet("name").build());
        LOG.info("Downstream {} objects found: {}", anyTypeKind, downstreamUIDs.size());

        // Step 2: use the pushed names as the expected set
        Set<String> syncopeExpectedUIDs = new HashSet<>(names);
        LOG.info("Syncope pushed {} names: {}", anyTypeKind, syncopeExpectedUIDs.size());

        // Step 3: delete downstream objects not present in pushed set
        int deleted = 0;
        for (String uid : downstreamUIDs) {
            if (!syncopeExpectedUIDs.contains(uid)) {
                LOG.info("Deleting downstream {} orphan: uid={}", anyTypeKind, uid);
                try {
                    profile.getConnector().delete(
                        objectClass,
                        new Uid(uid),
                        new OperationOptionsBuilder().build(),
                        new MutableBoolean());
                    deleted++;
                    recordOrphanDelete(profile, anyTypeKind, uid, null);
                } catch (Exception e) {
                    LOG.error("Failed to delete downstream {} orphan uid={}", anyTypeKind, uid, e);
                    recordOrphanDelete(profile, anyTypeKind, uid, e);
                }
            }
        }

        LOG.info("Downstream {} cleanup done. Deleted={}", anyTypeKind, deleted);
    }

    private void recordOrphanDelete(
            final ProvisioningProfile<?, ?> profile,
            final AnyTypeKind anyTypeKind,
            final String uid,
            final Exception error) {

        ProvisioningReport report = new ProvisioningReport();
        report.setOperation(ResourceOperation.DELETE);
        report.setAnyType(anyTypeKind.name());
        report.setName(uid);
        report.setUidValue(uid);
        if (error == null) {
            report.setStatus(ProvisioningReport.Status.SUCCESS);
        } else {
            report.setStatus(ProvisioningReport.Status.FAILURE);
            report.setMessage(ExceptionUtils.getRootCauseMessage(error));
        }
        profile.getResults().add(report);
    }

    private List<String> expectedDownstreamIdentifiers(
            final ProvisioningProfile<?, ?> profile,
            final ExternalResource resource,
            final AnyTypeKind anyTypeKind,
            final Provision provision) {

        return profile.getResults().stream()
                .filter(report -> anyTypeKind.name().equals(report.getAnyType()))
                .map(ProvisioningReport::getKey)
                .map(key -> expectedDownstreamIdentifier(key, anyTypeKind, resource, provision))
                .flatMap(Optional::stream)
                .distinct()
                .collect(Collectors.toList());
    }

    private Optional<String> expectedDownstreamIdentifier(
            final String key,
            final AnyTypeKind anyTypeKind,
            final ExternalResource resource,
            final Provision provision) {

        Optional<? extends Any> any = switch (anyTypeKind) {
            case USER -> userDAO.findById(key).map(User.class::cast);
            case GROUP -> groupDAO.findById(key).map(Group.class::cast);
            default -> Optional.empty();
        };

        if (any.isEmpty()) {
            LOG.debug("Could not find {} with key {} while computing downstream identifier", anyTypeKind, key);
            return Optional.empty();
        }

        return mappingManager.getConnObjectKeyValue(any.get(), resource, provision);
    }
}
