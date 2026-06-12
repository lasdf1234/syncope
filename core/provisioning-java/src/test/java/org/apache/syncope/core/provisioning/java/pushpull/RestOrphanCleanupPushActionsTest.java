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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
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
import org.apache.syncope.core.persistence.api.dao.GroupDAO;
import org.apache.syncope.core.persistence.api.dao.UserDAO;
import org.apache.syncope.core.persistence.api.entity.ExternalResource;
import org.apache.syncope.core.persistence.api.entity.group.Group;
import org.apache.syncope.core.persistence.api.entity.task.PushTask;
import org.apache.syncope.core.persistence.api.entity.user.User;
import org.apache.syncope.core.provisioning.api.Connector;
import org.apache.syncope.core.provisioning.api.MappingManager;
import org.apache.syncope.core.provisioning.api.pushpull.ProvisioningProfile;
import org.apache.syncope.core.provisioning.java.AbstractTest;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.SearchResult;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.spi.SearchResultsHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class RestOrphanCleanupPushActionsTest extends AbstractTest {

    @Mock
    private UserDAO userDAO;

    @Mock
    private GroupDAO groupDAO;

    @Mock
    private MappingManager mappingManager;

    @InjectMocks
    private RestOrphanCleanupPushActions actions;

    @Mock
    private ProvisioningProfile<?, ?> profile;

    @Mock
    private PushTask pushTask;

    @Mock
    private ExternalResource resource;

    @Mock
    private Connector connector;

    @Mock
    private User syncopeUser;

    @Mock
    private Group syncopeGroup;

    private List<ProvisioningReport> results;

    private static final String RESOURCE_KEY       = "test-resource";
    private static final String USER_KEY           = "user-key";
    private static final String GROUP_KEY          = "group-key";
    private static final String SYNCOPE_USER_UID   = "syncope-user-uid";
    private static final String ORPHAN_UID         = "orphan-uid";
    private static final String SYNCOPE_GROUP_UID  = "syncope-group-uid";
    private static final String ORPHAN_GROUP_UID   = "orphan-group-uid";

    private final Provision userProvision  = new Provision();
    private final Provision groupProvision = new Provision();

    @BeforeEach
    public void setUp() {
        userProvision.setAnyType(AnyTypeKind.USER.name());
        userProvision.setObjectClass("__ACCOUNT__");
        groupProvision.setAnyType(AnyTypeKind.GROUP.name());
        groupProvision.setObjectClass("__GROUP__");
        results = new ArrayList<>();

        lenient().doReturn(pushTask).when(profile).getTask();
        lenient().when(pushTask.getResource()).thenReturn(resource);
        lenient().when(resource.getKey()).thenReturn(RESOURCE_KEY);
        lenient().doReturn(connector).when(profile).getConnector();
        lenient().doReturn(results).when(profile).getResults();

        lenient().when(resource.getProvisionByAnyType(AnyTypeKind.USER.name()))
            .thenReturn(Optional.of(userProvision));
        lenient().when(resource.getProvisionByAnyType(AnyTypeKind.GROUP.name()))
            .thenReturn(Optional.of(groupProvision));

        lenient().when(syncopeUser.getKey()).thenReturn(USER_KEY);
        lenient().when(syncopeGroup.getKey()).thenReturn(GROUP_KEY);
    }

    // Helper: make connector.search() call handler with given UIDs
    private void stubConnectorSearch(ObjectClass objectClass, String... uids) {
        List<ConnectorObject> objects = new ArrayList<>();
        for (String uid : uids) {
            objects.add(new ConnectorObjectBuilder()
                .setObjectClass(objectClass)
                .setUid(uid)
                .setName(uid)
                .build());
        }
        stubConnectorSearchObjects(objectClass, objects.toArray(ConnectorObject[]::new));
    }

    private void stubConnectorSearchObjects(ObjectClass objectClass, ConnectorObject... objects) {
        doAnswer(inv -> {
            SearchResultsHandler handler = inv.getArgument(2);
            for (ConnectorObject obj : objects) {
                if (!handler.handle(obj)) {
                    break;
                }
            }
            return null;
        }).when(connector).search(eq(objectClass), any(), any(SearchResultsHandler.class), any());
    }

    // -------------------------------------------------------------------------
    // afterAll() — dry run skips connector delete
    // -------------------------------------------------------------------------

    @Test
    public void afterAllDryRunSkipsDeletion() {
        when(profile.isDryRun()).thenReturn(true);
        actions.afterAll(profile);
        verify(connector, never()).delete(any(), any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // afterAll() — downstream orphan user deleted
    // Downstream has ORPHAN_UID + SYNCOPE_USER_UID; Syncope only has SYNCOPE_USER_UID
    // -------------------------------------------------------------------------

    @Test
    public void afterAllDeletesOrphanInDownstream() {
        when(profile.isDryRun()).thenReturn(false);
        results.add(report(USER_KEY, AnyTypeKind.USER));

        ObjectClass accountClass = new ObjectClass("__ACCOUNT__");
        stubConnectorSearch(accountClass, SYNCOPE_USER_UID, ORPHAN_UID);

        doReturn(Optional.of(syncopeUser)).when(userDAO).findById(USER_KEY);
        when(mappingManager.getConnObjectKeyValue(eq(syncopeUser), eq(resource), eq(userProvision)))
            .thenReturn(Optional.of(SYNCOPE_USER_UID));

        actions.afterAll(profile);

        // SYNCOPE_USER_UID is in Syncope → NOT deleted
        verify(connector, never()).delete(eq(accountClass), eq(new Uid(SYNCOPE_USER_UID)), any(), any());
        // ORPHAN_UID is NOT in Syncope → deleted
        verify(connector, times(1)).delete(eq(accountClass), eq(new Uid(ORPHAN_UID)), any(), any());
        assertEquals(1, orphanDeleteReports(AnyTypeKind.USER, ProvisioningReport.Status.SUCCESS).size());
        assertEquals(ORPHAN_UID, orphanDeleteReports(AnyTypeKind.USER, ProvisioningReport.Status.SUCCESS).get(0).getName());
    }

    // -------------------------------------------------------------------------
    // afterAll() — no orphans, nothing deleted
    // -------------------------------------------------------------------------

    @Test
    public void afterAllNoOrphansDeletesNothing() {
        when(profile.isDryRun()).thenReturn(false);
        results.add(report(USER_KEY, AnyTypeKind.USER));

        ObjectClass accountClass = new ObjectClass("__ACCOUNT__");
        stubConnectorSearch(accountClass, SYNCOPE_USER_UID);

        doReturn(Optional.of(syncopeUser)).when(userDAO).findById(USER_KEY);
        when(mappingManager.getConnObjectKeyValue(eq(syncopeUser), eq(resource), eq(userProvision)))
            .thenReturn(Optional.of(SYNCOPE_USER_UID));

        actions.afterAll(profile);

        verify(connector, never()).delete(any(), any(), any(), any());
        assertTrue(orphanDeleteReports(AnyTypeKind.USER, ProvisioningReport.Status.SUCCESS).isEmpty());
        assertTrue(orphanDeleteReports(AnyTypeKind.USER, ProvisioningReport.Status.FAILURE).isEmpty());
    }

    // -------------------------------------------------------------------------
    // afterAll() — downstream group orphan deleted
    // -------------------------------------------------------------------------

    @Test
    public void afterAllDeletesOrphanGroupInDownstream() {
        when(profile.isDryRun()).thenReturn(false);
        results.add(report(GROUP_KEY, AnyTypeKind.GROUP));

        ObjectClass accountClass = new ObjectClass("__ACCOUNT__");
        ObjectClass groupClass   = new ObjectClass("__GROUP__");
        stubConnectorSearch(accountClass); // no users
        stubConnectorSearch(groupClass, SYNCOPE_GROUP_UID, ORPHAN_GROUP_UID);

        doReturn(Optional.of(syncopeGroup)).when(groupDAO).findById(GROUP_KEY);
        when(mappingManager.getConnObjectKeyValue(eq(syncopeGroup), eq(resource), eq(groupProvision)))
            .thenReturn(Optional.of(SYNCOPE_GROUP_UID));

        actions.afterAll(profile);

        verify(connector, never()).delete(eq(groupClass), eq(new Uid(SYNCOPE_GROUP_UID)), any(), any());
        verify(connector, times(1)).delete(eq(groupClass), eq(new Uid(ORPHAN_GROUP_UID)), any(), any());
        assertEquals(1, orphanDeleteReports(AnyTypeKind.GROUP, ProvisioningReport.Status.SUCCESS).size());
    }

    // -------------------------------------------------------------------------
    // afterAll() — no USER provision on resource → skip user check
    // -------------------------------------------------------------------------

    @Test
    public void afterAllNoUserProvisionSkipsUserCleanup() {
        when(profile.isDryRun()).thenReturn(false);
        when(resource.getProvisionByAnyType(AnyTypeKind.USER.name())).thenReturn(Optional.empty());

        ObjectClass groupClass = new ObjectClass("__GROUP__");
        stubConnectorSearch(groupClass); // no group orphans either

        actions.afterAll(profile);

        verify(connector, never()).delete(any(), any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // afterAll() — user with no connKey mapping is not counted as expected → not protected
    // -------------------------------------------------------------------------

    @Test
    public void afterAllUserWithNoConnKeyDoesNotProtectDownstreamObject() {
        when(profile.isDryRun()).thenReturn(false);
        results.add(report(USER_KEY, AnyTypeKind.USER));

        ObjectClass accountClass = new ObjectClass("__ACCOUNT__");
        stubConnectorSearch(accountClass, ORPHAN_UID);

        // Syncope has a user but mapping returns empty → ORPHAN_UID not protected
        doReturn(Optional.of(syncopeUser)).when(userDAO).findById(USER_KEY);
        when(mappingManager.getConnObjectKeyValue(eq(syncopeUser), eq(resource), eq(userProvision)))
            .thenReturn(Optional.empty());

        actions.afterAll(profile);

        verify(connector, times(1)).delete(eq(accountClass), eq(new Uid(ORPHAN_UID)), any(), any());
    }

    @Test
    public void afterAllUsesTransformedConnObjectKeyInsteadOfReportName() {
        when(profile.isDryRun()).thenReturn(false);

        ProvisioningReport report = report(USER_KEY, AnyTypeKind.USER);
        report.setName("raw-username");
        results.add(report);

        ObjectClass accountClass = new ObjectClass("__ACCOUNT__");
        stubConnectorSearch(accountClass, "transformed-username");

        doReturn(Optional.of(syncopeUser)).when(userDAO).findById(USER_KEY);
        when(mappingManager.getConnObjectKeyValue(eq(syncopeUser), eq(resource), eq(userProvision)))
                .thenReturn(Optional.of("transformed-username"));

        actions.afterAll(profile);

        verify(connector, never()).delete(eq(accountClass), eq(new Uid("transformed-username")), any(), any());
    }

    @Test
    public void afterAllFallsBackToUidWhenNameAttributeMissing() {
        when(profile.isDryRun()).thenReturn(false);
        results.add(report(USER_KEY, AnyTypeKind.USER));

        ObjectClass accountClass = new ObjectClass("__ACCOUNT__");
        ConnectorObject downstream = org.mockito.Mockito.mock(ConnectorObject.class);
        Attribute emptyName = org.mockito.Mockito.mock(Attribute.class);
        when(emptyName.getValue()).thenReturn(List.of());
        when(downstream.getAttributeByName("name")).thenReturn(emptyName);
        when(downstream.getUid()).thenReturn(new Uid(SYNCOPE_USER_UID));
        stubConnectorSearchObjects(accountClass, downstream);

        doReturn(Optional.of(syncopeUser)).when(userDAO).findById(USER_KEY);
        when(mappingManager.getConnObjectKeyValue(eq(syncopeUser), eq(resource), eq(userProvision)))
                .thenReturn(Optional.of(SYNCOPE_USER_UID));

        actions.afterAll(profile);

        verify(connector, never()).delete(eq(accountClass), eq(new Uid(SYNCOPE_USER_UID)), any(), any());
    }

    @Test
    public void afterAllMissingSyncopeEntityDoesNotProtectDownstreamObject() {
        when(profile.isDryRun()).thenReturn(false);
        results.add(report(USER_KEY, AnyTypeKind.USER));

        ObjectClass accountClass = new ObjectClass("__ACCOUNT__");
        stubConnectorSearch(accountClass, ORPHAN_UID);

        doReturn(Optional.empty()).when(userDAO).findById(USER_KEY);

        actions.afterAll(profile);

        verify(connector, times(1)).delete(eq(accountClass), eq(new Uid(ORPHAN_UID)), any(), any());
        verify(mappingManager, never()).getConnObjectKeyValue(any(), any(), any());
    }

    @Test
    public void afterAllDeleteFailureDoesNotStopCleanup() {
        when(profile.isDryRun()).thenReturn(false);

        ObjectClass accountClass = new ObjectClass("__ACCOUNT__");
        stubConnectorSearch(accountClass, ORPHAN_UID, "second-orphan");

        doAnswer(inv -> {
            Uid uid = inv.getArgument(1);
            if (ORPHAN_UID.equals(uid.getUidValue())) {
                throw new RuntimeException("delete failed");
            }
            return null;
        }).when(connector).delete(eq(accountClass), any(Uid.class), any(), any());

        actions.afterAll(profile);

        verify(connector, times(1)).delete(eq(accountClass), eq(new Uid(ORPHAN_UID)), any(), any());
        verify(connector, times(1)).delete(eq(accountClass), eq(new Uid("second-orphan")), any(), any());
        assertEquals(1, orphanDeleteReports(AnyTypeKind.USER, ProvisioningReport.Status.FAILURE).size());
        assertEquals(1, orphanDeleteReports(AnyTypeKind.USER, ProvisioningReport.Status.SUCCESS).size());
        assertEquals("second-orphan",
                orphanDeleteReports(AnyTypeKind.USER, ProvisioningReport.Status.SUCCESS).get(0).getName());
    }

    @Test
    public void afterAllSearchFailureDoesNotPropagate() {
        when(profile.isDryRun()).thenReturn(false);
        results.add(report(USER_KEY, AnyTypeKind.USER));

        ObjectClass accountClass = new ObjectClass("__ACCOUNT__");
        ObjectClass groupClass = new ObjectClass("__GROUP__");
        doAnswer(inv -> {
            throw new RuntimeException("Search script error");
        }).when(connector).search(eq(accountClass), any(), any(SearchResultsHandler.class), any());
        stubConnectorSearch(groupClass);

        actions.afterAll(profile);

        verify(connector, never()).delete(any(), any(), any(), any());
        verify(connector).search(eq(groupClass), any(), any(SearchResultsHandler.class), any());
    }

    @Test
    public void afterAllResourceSetupFailureDoesNotPropagate() {
        when(profile.isDryRun()).thenReturn(false);
        when(profile.getTask()).thenThrow(new RuntimeException("task unavailable"));

        actions.afterAll(profile);

        verify(connector, never()).search(any(), any(), any(), any());
    }

    @Test
    public void afterAllNoGroupProvisionSkipsGroupCleanup() {
        when(profile.isDryRun()).thenReturn(false);
        when(resource.getProvisionByAnyType(AnyTypeKind.GROUP.name())).thenReturn(Optional.empty());

        ObjectClass accountClass = new ObjectClass("__ACCOUNT__");
        stubConnectorSearch(accountClass);

        actions.afterAll(profile);

        verify(connector, never()).search(eq(new ObjectClass("__GROUP__")), any(), any(SearchResultsHandler.class), any());
        verify(connector, never()).delete(any(), any(), any(), any());
    }

    @Test
    public void onErrorNeverDeletesUser() {
        Exception error = new RuntimeException("org.flowable.common.engine.api.FlowableException: 2 process instances");

        actions.onError(profile, syncopeUser, new ProvisioningReport(), error);

        verify(userDAO, never()).deleteById(anyString());
    }

    private ProvisioningReport report(final String key, final AnyTypeKind anyTypeKind) {
        ProvisioningReport report = new ProvisioningReport();
        report.setKey(key);
        report.setAnyType(anyTypeKind.name());
        report.setName(UUID.randomUUID().toString());
        return report;
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
