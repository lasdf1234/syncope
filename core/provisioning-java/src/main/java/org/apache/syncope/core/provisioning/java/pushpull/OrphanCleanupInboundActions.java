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

import java.util.Set;
import java.util.stream.Collectors;
import org.apache.syncope.common.lib.types.AnyTypeKind;
import org.apache.syncope.common.lib.types.PullMode;
import org.apache.syncope.core.persistence.api.dao.ExternalResourceDAO;
import org.apache.syncope.core.persistence.api.dao.GroupDAO;
import org.apache.syncope.core.persistence.api.dao.UserDAO;
import org.apache.syncope.core.persistence.api.entity.ExternalResource;
import org.apache.syncope.core.persistence.api.entity.task.PullTask;
import org.apache.syncope.core.provisioning.api.pushpull.InboundActions;
import org.apache.syncope.core.provisioning.api.pushpull.ProvisioningProfile;
import org.apache.syncope.core.spring.implementation.InstanceScope;
import org.apache.syncope.core.spring.implementation.SyncopeImplementation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;

/**
 * InboundActions implementation that deletes Syncope objects which no longer exist in the upstream system.
 *
 * After a full Pull completes, any Syncope object whose mapped connector-object-key value
 * is absent from the upstream pull results is considered an orphan and deleted from Syncope.
 * Detection is based on the resource's mapping (connObjectKey attribute), so users do NOT
 * need to be explicitly linked to the resource beforehand.
 *
 * <p>Requires: {@code pullMode=FULL_RECONCILIATION} on the pull task.
 * Task startup will fail with {@link IllegalStateException} if misconfigured.
 */
@SyncopeImplementation(scope = InstanceScope.PER_CONTEXT)
public class OrphanCleanupInboundActions implements InboundActions {

    private static final Logger LOG = LoggerFactory.getLogger(OrphanCleanupInboundActions.class);

    @Autowired
    private UserDAO userDAO;

    @Autowired
    private GroupDAO groupDAO;

    @Autowired
    private ExternalResourceDAO resourceDAO;

    // -------------------------------------------------------------------------
    // beforeAll() — validate task configuration before processing starts
    // -------------------------------------------------------------------------

    @Override
    public void beforeAll(final ProvisioningProfile<?, ?> profile) {
        if (profile.getTask() instanceof PullTask pullTask) {
            if (pullTask.getPullMode() != PullMode.FULL_RECONCILIATION) {
                throw new IllegalStateException(
                    "OrphanCleanupInboundActions requires pullMode=FULL_RECONCILIATION, but got: "
                    + pullTask.getPullMode()
                    + ". Incremental pull cannot reliably detect orphans.");
            }
            LOG.info("Task configuration validated: pullMode=FULL_RECONCILIATION");
        }
    }

    // -------------------------------------------------------------------------
    // afterAll() — compare pull results vs Syncope, delete orphans
    // -------------------------------------------------------------------------

    @Override
    public void afterAll(final ProvisioningProfile<?, ?> profile) {
        if (profile.isDryRun()) {
            LOG.info("[DRY-RUN] Skip orphan cleanup");
            return;
        }

        String resourceKey = profile.getTask().getResource().getKey();
        LOG.info("Orphan cleanup started, resource={}", resourceKey);

        // Step 1: collect ALL upstream names returned during this pull
        // (every result has name set — it's the connector object name/uid from upstream)
        Set<String> allUpstreamNames = profile.getResults().stream()
            .map(r -> r.getName())
            .filter(name -> name != null)
            .collect(Collectors.toSet());
        LOG.info("Total upstream Names seen in pull results: {}, values={}", allUpstreamNames.size(), allUpstreamNames);

        ExternalResource resource = resourceDAO.findById(resourceKey).orElse(null);
        if (resource == null) {
            LOG.warn("Resource '{}' not found, aborting orphan cleanup", resourceKey);
            return;
        }

        // Step 2: delete orphan users
        int deletedUsers = 0;
        if (resource.getProvisionByAnyType(AnyTypeKind.USER.name()).isPresent()) {
            for (var user : userDAO.findAll(Pageable.unpaged()).getContent()) {
                if ("admin".equals(user.getUsername())) {
                    LOG.debug("Skipping built-in admin user from orphan cleanup");
                    continue;
                }
                if (!allUpstreamNames.contains(user.getUsername())) {
                    LOG.info("Deleting orphan user: username={}, key={}", user.getUsername(), user.getKey());
                    try {
                        userDAO.deleteById(user.getKey());
                        deletedUsers++;
                    } catch (Exception e) {
                        LOG.error("Failed to delete orphan user key={}", user.getKey(), e);
                    }
                }
            }
        } else {
            LOG.debug("No USER provision found on resource '{}', skipping user orphan check", resourceKey);
        }

        // Step 3: delete orphan groups
        int deletedGroups = 0;
        if (resource.getProvisionByAnyType(AnyTypeKind.GROUP.name()).isPresent()) {
            for (var group : groupDAO.findAll(Pageable.unpaged()).getContent()) {
                if (!allUpstreamNames.contains(group.getName())) {
                    LOG.info("Deleting orphan group: name={}, key={}", group.getName(), group.getKey());
                    try {
                        groupDAO.deleteById(group.getKey());
                        deletedGroups++;
                    } catch (Exception e) {
                        LOG.error("Failed to delete orphan group key={}", group.getKey(), e);
                    }
                }
            }
        } else {
            LOG.debug("No GROUP provision found on resource '{}', skipping group orphan check", resourceKey);
        }

        LOG.info("Orphan cleanup done. Deleted users={}, groups={}", deletedUsers, deletedGroups);
    }
}
