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
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.syncope.common.lib.to.ProvisioningReport;
import org.apache.syncope.common.lib.types.AnyTypeKind;
import org.apache.syncope.common.lib.types.ResourceOperation;
import org.apache.syncope.core.persistence.api.dao.ExternalResourceDAO;
import org.apache.syncope.core.persistence.api.dao.GroupDAO;
import org.apache.syncope.core.persistence.api.dao.UserDAO;
import org.apache.syncope.core.persistence.api.entity.ExternalResource;
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
 * After a Pull completes, any Syncope object whose mapped connector-object-key value
 * is absent from the upstream pull results is considered an orphan and deleted from Syncope.
 * Detection is based on the resource's mapping (connObjectKey attribute), so users do NOT
 * need to be explicitly linked to the resource beforehand.
 *
 * <p>Orphan detection is scoped to objects returned by the pull run. With
 * {@code FILTERED_RECONCILIATION}, only objects matching the recon filter are considered
 * upstream; Syncope objects outside that scope may be deleted as orphans.
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

    @Override
    public void afterAll(final ProvisioningProfile<?, ?> profile) {
        if (profile.isDryRun()) {
            LOG.info("[DRY-RUN] Skip orphan cleanup");
            return;
        }

        try {
            String resourceKey = profile.getTask().getResource().getKey();
            LOG.info("Orphan cleanup started, resource={}", resourceKey);
            Set<String> allUpstreamNames = profile.getResults().stream()
                .map(ProvisioningReport::getName)
                .filter(name -> name != null)
                .collect(Collectors.toSet());
            LOG.info("Total upstream Names seen in pull results: {}, values={}",
                    allUpstreamNames.size(), allUpstreamNames);

            ExternalResource resource = resourceDAO.findById(resourceKey).orElse(null);
            if (resource == null) {
                LOG.warn("Resource '{}' not found, aborting orphan cleanup", resourceKey);
                return;
            }

            try {
                deletedUsers(profile, resource, allUpstreamNames);
            } catch (Exception e) {
                LOG.error("USER orphan cleanup failed, skipping", e);
            }

            try {
                deletedGroups(profile, resource, allUpstreamNames);
            } catch (Exception e) {
                LOG.error("GROUP orphan cleanup failed, skipping", e);
            }
        } catch (Exception e) {
            LOG.error("Orphan cleanup failed before processing", e);
        }
    }

    private void deletedUsers(
            final ProvisioningProfile<?, ?> profile,
            final ExternalResource resource,
            final Set<String> allUpstreamNames) {

        int deletedUsers = 0;
        if (resource.getProvisionByAnyType(AnyTypeKind.USER.name()).isPresent()) {
            for (var user : userDAO.findAll(Pageable.unpaged()).getContent()) {
                if (!allUpstreamNames.contains(user.getUsername())) {
                    LOG.info("Deleting orphan user: username={}, key={}", user.getUsername(), user.getKey());
                    try {
                        userDAO.deleteById(user.getKey());
                        deletedUsers++;
                        recordOrphanDelete(
                                profile, AnyTypeKind.USER, user.getKey(), user.getUsername(), null);
                    } catch (Exception e) {
                        LOG.error("Failed to delete orphan user key={}", user.getKey(), e);
                        recordOrphanDelete(
                                profile, AnyTypeKind.USER, user.getKey(), user.getUsername(), e);
                    }
                }
            }
        } else {
            LOG.debug("No USER provision found on resource '{}', skipping user orphan check",
                    resource.getKey());
        }

        LOG.info("Orphan USER cleanup done. Deleted={}", deletedUsers);
    }

    private void deletedGroups(
            final ProvisioningProfile<?, ?> profile,
            final ExternalResource resource,
            final Set<String> allUpstreamNames) {

        int deletedGroups = 0;
        if (resource.getProvisionByAnyType(AnyTypeKind.GROUP.name()).isPresent()) {
            for (var group : groupDAO.findAll(Pageable.unpaged()).getContent()) {
                if (!allUpstreamNames.contains(group.getName())) {
                    LOG.info("Deleting orphan group: name={}, key={}", group.getName(), group.getKey());
                    try {
                        groupDAO.deleteById(group.getKey());
                        deletedGroups++;
                        recordOrphanDelete(
                                profile, AnyTypeKind.GROUP, group.getKey(), group.getName(), null);
                    } catch (Exception e) {
                        LOG.error("Failed to delete orphan group key={}", group.getKey(), e);
                        recordOrphanDelete(
                                profile, AnyTypeKind.GROUP, group.getKey(), group.getName(), e);
                    }
                }
            }
        } else {
            LOG.debug("No GROUP provision found on resource '{}', skipping group orphan check",
                    resource.getKey());
        }

        LOG.info("Orphan GROUP cleanup done. Deleted={}", deletedGroups);
    }

    private void recordOrphanDelete(
            final ProvisioningProfile<?, ?> profile,
            final AnyTypeKind anyTypeKind,
            final String key,
            final String name,
            final Exception error) {

        ProvisioningReport report = new ProvisioningReport();
        report.setOperation(ResourceOperation.DELETE);
        report.setAnyType(anyTypeKind.name());
        report.setKey(key);
        report.setName(name);
        report.setUidValue(name);
        if (error == null) {
            report.setStatus(ProvisioningReport.Status.SUCCESS);
        } else {
            report.setStatus(ProvisioningReport.Status.FAILURE);
            report.setMessage(ExceptionUtils.getRootCauseMessage(error));
        }
        profile.getResults().add(report);
    }
}
