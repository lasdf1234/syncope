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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.syncope.common.lib.to.Provision;
import org.apache.syncope.common.lib.to.ProvisioningReport;
import org.apache.syncope.common.lib.types.AnyTypeKind;
import org.apache.syncope.common.lib.types.ResourceOperation;
import org.apache.syncope.core.persistence.api.dao.ExternalResourceDAO;
import org.apache.syncope.core.persistence.api.dao.GroupDAO;
import org.apache.syncope.core.persistence.api.dao.UserDAO;
import org.apache.syncope.core.persistence.api.entity.ExternalResource;
import org.apache.syncope.core.persistence.api.entity.group.Group;
import org.apache.syncope.core.persistence.api.entity.task.PullTask;
import org.apache.syncope.core.persistence.api.entity.user.User;
import org.apache.syncope.core.provisioning.api.pushpull.ProvisioningProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
public class OrphanCleanupInboundActionsTest {

    @Mock
    private UserDAO userDAO;

    @Mock
    private GroupDAO groupDAO;

    @Mock
    private ExternalResourceDAO resourceDAO;

    @InjectMocks
    private OrphanCleanupInboundActions actions;

    @Mock
    private ProvisioningProfile<?, ?> profile;

    @Mock
    private PullTask pullTask;

    @Mock
    private ExternalResource resource;

    @Mock
    private User orphanUser;

    @Mock
    private User matchedUser;

    @Mock
    private Group orphanGroup;

    @Mock
    private Group matchedGroup;

    private static final String RESOURCE_KEY       = "test-resource";
    private static final String ORPHAN_USER_KEY    = UUID.randomUUID().toString();
    private static final String MATCHED_USER_KEY   = UUID.randomUUID().toString();
    private static final String ORPHAN_GROUP_KEY   = UUID.randomUUID().toString();
    private static final String MATCHED_GROUP_KEY  = UUID.randomUUID().toString();

    // Upstream usernames (= __UID__ values from the connector)
    private static final String ORPHAN_USERNAME   = "orphan@example.com";
    private static final String MATCHED_USERNAME  = "matched@example.com";
    private static final String ORPHAN_GROUP_NAME  = "orphan-group";
    private static final String MATCHED_GROUP_NAME = "matched-group";

    private final Provision userProvision  = new Provision();
    private final Provision groupProvision = new Provision();
    private List<ProvisioningReport> results;

    @BeforeEach
    public void setUp() {
        userProvision.setAnyType(AnyTypeKind.USER.name());
        groupProvision.setAnyType(AnyTypeKind.GROUP.name());
        results = new ArrayList<>();

        lenient().doReturn(pullTask).when(profile).getTask();
        lenient().when(pullTask.getResource()).thenReturn(resource);
        lenient().when(resource.getKey()).thenReturn(RESOURCE_KEY);
        lenient().doReturn(Optional.of(resource)).when(resourceDAO).findById(RESOURCE_KEY);
        lenient().doReturn(results).when(profile).getResults();

        lenient().when(resource.getProvisionByAnyType(AnyTypeKind.USER.name()))
            .thenReturn(Optional.of(userProvision));
        lenient().when(resource.getProvisionByAnyType(AnyTypeKind.GROUP.name()))
            .thenReturn(Optional.of(groupProvision));

        lenient().when(orphanUser.getKey()).thenReturn(ORPHAN_USER_KEY);
        lenient().when(orphanUser.getUsername()).thenReturn(ORPHAN_USERNAME);

        lenient().when(matchedUser.getKey()).thenReturn(MATCHED_USER_KEY);
        lenient().when(matchedUser.getUsername()).thenReturn(MATCHED_USERNAME);

        lenient().when(orphanGroup.getKey()).thenReturn(ORPHAN_GROUP_KEY);
        lenient().when(orphanGroup.getName()).thenReturn(ORPHAN_GROUP_NAME);

        lenient().when(matchedGroup.getKey()).thenReturn(MATCHED_GROUP_KEY);
        lenient().when(matchedGroup.getName()).thenReturn(MATCHED_GROUP_NAME);

        // Default: empty findAll
        lenient().doReturn(new PageImpl<>(List.of())).when(userDAO).findAll(any(Pageable.class));
        lenient().doReturn(new PageImpl<>(List.of())).when(groupDAO).findAll(any(Pageable.class));
    }

    @Test
    public void afterAllDryRunSkipsDeletion() {
        when(profile.isDryRun()).thenReturn(true);
        actions.afterAll(profile);
        verify(userDAO, never()).deleteById(anyString());
        verify(groupDAO, never()).deleteById(anyString());
    }

    // -------------------------------------------------------------------------
    // afterAll() — orphan user deleted, matched user kept
    // upstream returned only MATCHED_USERNAME → orphanUser absent → deleted
    // -------------------------------------------------------------------------

    @Test
    public void afterAllDeletesOrphanUser() {
        when(profile.isDryRun()).thenReturn(false);

        ProvisioningReport matchedResult = new ProvisioningReport();
        matchedResult.setName(MATCHED_USERNAME);
        results.add(matchedResult);

        lenient().doReturn(new PageImpl<>(List.of(orphanUser, matchedUser)))
            .when(userDAO).findAll(any(Pageable.class));

        actions.afterAll(profile);

        verify(userDAO, times(1)).deleteById(ORPHAN_USER_KEY);
        verify(userDAO, never()).deleteById(MATCHED_USER_KEY);
        assertEquals(1, orphanDeleteReports(AnyTypeKind.USER, ProvisioningReport.Status.SUCCESS).size());
        assertEquals(ORPHAN_USERNAME,
                orphanDeleteReports(AnyTypeKind.USER, ProvisioningReport.Status.SUCCESS).get(0).getName());
    }

    // -------------------------------------------------------------------------
    // afterAll() — orphan group deleted
    // -------------------------------------------------------------------------

    @Test
    public void afterAllDeletesOrphanGroup() {
        when(profile.isDryRun()).thenReturn(false);

        lenient().doReturn(new PageImpl<>(List.of(orphanGroup)))
            .when(groupDAO).findAll(any(Pageable.class));

        actions.afterAll(profile);

        verify(groupDAO, times(1)).deleteById(ORPHAN_GROUP_KEY);
        assertEquals(1, orphanDeleteReports(AnyTypeKind.GROUP, ProvisioningReport.Status.SUCCESS).size());
    }

    // -------------------------------------------------------------------------
    // afterAll() — matched group kept, orphan group deleted
    // -------------------------------------------------------------------------

    @Test
    public void afterAllKeepsMatchedGroup() {
        when(profile.isDryRun()).thenReturn(false);

        ProvisioningReport r = new ProvisioningReport();
        r.setName(MATCHED_GROUP_NAME);
        results.add(r);

        lenient().doReturn(new PageImpl<>(List.of(orphanGroup, matchedGroup)))
            .when(groupDAO).findAll(any(Pageable.class));

        actions.afterAll(profile);

        verify(groupDAO, times(1)).deleteById(ORPHAN_GROUP_KEY);
        verify(groupDAO, never()).deleteById(MATCHED_GROUP_KEY);
    }

    // -------------------------------------------------------------------------
    // afterAll() — no orphans when upstream has all objects
    // -------------------------------------------------------------------------

    @Test
    public void afterAllNoOrphansDeletesNothing() {
        when(profile.isDryRun()).thenReturn(false);

        ProvisioningReport r = new ProvisioningReport();
        r.setName(MATCHED_USERNAME);
        results.add(r);

        lenient().doReturn(new PageImpl<>(List.of(matchedUser)))
            .when(userDAO).findAll(any(Pageable.class));

        actions.afterAll(profile);

        verify(userDAO, never()).deleteById(anyString());
        verify(groupDAO, never()).deleteById(anyString());
        assertTrue(orphanDeleteReports(AnyTypeKind.USER, ProvisioningReport.Status.SUCCESS).isEmpty());
        assertTrue(orphanDeleteReports(AnyTypeKind.USER, ProvisioningReport.Status.FAILURE).isEmpty());
    }

    @Test
    public void afterAllDeleteFailureIsRecorded() {
        when(profile.isDryRun()).thenReturn(false);

        lenient().doReturn(new PageImpl<>(List.of(orphanUser)))
            .when(userDAO).findAll(any(Pageable.class));
        doThrow(new RuntimeException("delete failed")).when(userDAO).deleteById(ORPHAN_USER_KEY);

        actions.afterAll(profile);

        assertEquals(1, orphanDeleteReports(AnyTypeKind.USER, ProvisioningReport.Status.FAILURE).size());
        assertEquals(ORPHAN_USER_KEY,
                orphanDeleteReports(AnyTypeKind.USER, ProvisioningReport.Status.FAILURE).get(0).getKey());
    }

    @Test
    public void afterAllResourceSetupFailureDoesNotPropagate() {
        when(profile.isDryRun()).thenReturn(false);
        when(profile.getTask()).thenThrow(new RuntimeException("task unavailable"));

        actions.afterAll(profile);

        verify(userDAO, never()).deleteById(anyString());
        verify(groupDAO, never()).deleteById(anyString());
    }

    // -------------------------------------------------------------------------
    // afterAll() — resource not found aborts cleanup
    // -------------------------------------------------------------------------

    @Test
    public void afterAllResourceNotFoundAbortsCleanup() {
        when(profile.isDryRun()).thenReturn(false);
        when(resourceDAO.findById(RESOURCE_KEY)).thenReturn(Optional.empty());

        actions.afterAll(profile);

        verify(userDAO, never()).deleteById(anyString());
        verify(groupDAO, never()).deleteById(anyString());
    }

    // -------------------------------------------------------------------------
    // afterAll() — no USER provision on resource skips user check
    // -------------------------------------------------------------------------

    @Test
    public void afterAllNoUserProvisionSkipsUserCheck() {
        when(profile.isDryRun()).thenReturn(false);
        when(resource.getProvisionByAnyType(AnyTypeKind.USER.name())).thenReturn(Optional.empty());

        lenient().doReturn(new PageImpl<>(List.of(orphanUser)))
            .when(userDAO).findAll(any(Pageable.class));

        actions.afterAll(profile);

        verify(userDAO, never()).deleteById(anyString());
    }

    // -------------------------------------------------------------------------
    // afterAll() — no GROUP provision on resource skips group check
    // -------------------------------------------------------------------------

    @Test
    public void afterAllNoGroupProvisionSkipsGroupCheck() {
        when(profile.isDryRun()).thenReturn(false);
        when(resource.getProvisionByAnyType(AnyTypeKind.GROUP.name())).thenReturn(Optional.empty());

        lenient().doReturn(new PageImpl<>(List.of(orphanGroup)))
            .when(groupDAO).findAll(any(Pageable.class));

        actions.afterAll(profile);

        verify(groupDAO, never()).deleteById(anyString());
    }

    private List<ProvisioningReport> orphanDeleteReports(
            final AnyTypeKind anyTypeKind,
            final ProvisioningReport.Status status) {

        return results.stream()
                .filter(r -> anyTypeKind.name().equals(r.getAnyType()))
                .filter(r -> ResourceOperation.DELETE == r.getOperation())
                .filter(r -> status == r.getStatus())
                .toList();
    }
}
