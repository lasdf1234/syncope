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
package org.apache.syncope.core.logic;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.syncope.common.lib.SyncopeConstants;
import org.apache.syncope.common.lib.types.CipherAlgorithm;
import org.apache.syncope.core.persistence.api.Encryptor;
import org.apache.syncope.core.persistence.api.EncryptorManager;
import org.apache.syncope.core.persistence.api.dao.AccessTokenDAO;
import org.apache.syncope.core.provisioning.api.data.AccessTokenDataBinder;
import org.apache.syncope.core.spring.security.SecurityProperties;
import org.apache.syncope.core.spring.security.SyncopeAuthenticationDetails;
import org.apache.syncope.core.spring.security.SyncopeGrantedAuthority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;

class AccessTokenLogicTest {

    @AfterEach
    void cleanupAuthContext() {
        SecurityContextHolder.getContext().setAuthentication(null);
    }

    @Test
    void tokenShouldIssueOneYearExpiration() throws Exception {
        List<SyncopeGrantedAuthority> authorities = List.of(
                new SyncopeGrantedAuthority("DUMMY", SyncopeConstants.ROOT_REALM));
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                new User("alice", "FAKE_PASSWORD", authorities),
                "FAKE_PASSWORD",
                authorities);
        auth.setDetails(new SyncopeAuthenticationDetails(SyncopeConstants.MASTER_DOMAIN, null));
        SecurityContextHolder.getContext().setAuthentication(auth);

        SecurityProperties securityProperties = new SecurityProperties();
        securityProperties.setAnonymousUser("anonymous");

        Encryptor encryptor = mock(Encryptor.class);
        when(encryptor.encode(anyString(), eq(CipherAlgorithm.AES))).thenReturn("authorities");

        EncryptorManager encryptorManager = mock(EncryptorManager.class);
        when(encryptorManager.getInstance()).thenReturn(encryptor);

        AccessTokenDataBinder binder = mock(AccessTokenDataBinder.class);
        when(binder.createWithExpiration(
                eq(Optional.empty()),
                eq("alice"),
                eq(Map.of()),
                anyString(),
                eq(false),
                argThat(expiration -> expiration != null
                && !expiration.isBefore(OffsetDateTime.now().plusYears(1).minusMinutes(1))
                && !expiration.isAfter(OffsetDateTime.now().plusYears(1).plusMinutes(1))))).thenReturn(null);

        AccessTokenLogic logic = new AccessTokenLogic(
                securityProperties,
                encryptorManager,
                binder,
                mock(AccessTokenDAO.class));

        logic.token();

        verify(binder).createWithExpiration(
                eq(Optional.empty()),
                eq("alice"),
                eq(Map.of()),
                anyString(),
                eq(false),
                argThat(expiration -> expiration != null
                && !expiration.isBefore(OffsetDateTime.now().plusYears(1).minusMinutes(1))
                && !expiration.isAfter(OffsetDateTime.now().plusYears(1).plusMinutes(1))));
    }
}
