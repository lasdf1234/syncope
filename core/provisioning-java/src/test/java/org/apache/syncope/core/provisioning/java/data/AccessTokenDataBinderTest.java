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
11.  *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.syncope.core.provisioning.java.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.SignedJWT;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import org.apache.syncope.core.persistence.api.dao.AccessTokenDAO;
import org.apache.syncope.core.persistence.api.entity.EntityFactory;
import org.apache.syncope.core.persistence.jpa.entity.JPAAccessToken;
import org.apache.syncope.core.provisioning.api.data.AccessTokenDataBinder;
import org.apache.syncope.core.provisioning.java.DummyConfParamOps;
import org.apache.syncope.core.spring.security.DefaultCredentialChecker;
import org.apache.syncope.core.spring.security.SecurityProperties;
import org.apache.syncope.core.spring.security.jws.AccessTokenJWSSigner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccessTokenDataBinderTest {

    @Mock
    private AccessTokenDAO accessTokenDAO;

    @Mock
    private EntityFactory entityFactory;

    @Test
    void createWithExpirationShouldHonorExpiration() throws Exception {
        SecurityProperties securityProperties = new SecurityProperties();
        securityProperties.setJwtIssuer("ApacheSyncope");
        securityProperties.setJwsAlgorithm(JWSAlgorithm.HS512.getName());
        securityProperties.setJwsKey("0123456789012345678901234567890123456789012345678901234567890123");
        securityProperties.setAdminUser("admin");

        AccessTokenJWSSigner signer = new AccessTokenJWSSigner(
                JWSAlgorithm.parse(securityProperties.getJwsAlgorithm()),
                securityProperties.getJwsKey());

        JPAAccessToken accessToken = new JPAAccessToken();
        when(accessTokenDAO.findByOwner("alice")).thenReturn(Optional.empty());
        when(entityFactory.newEntity(any())).thenReturn(accessToken);
        when(accessTokenDAO.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AccessTokenDataBinder binder = new AccessTokenDataBinderImpl(
                securityProperties,
                signer,
                accessTokenDAO,
                new DummyConfParamOps(),
                entityFactory,
                new DefaultCredentialChecker("custom-jws-key", "custom-admin-password", "custom-anonymous-key"));

        OffsetDateTime expiration = OffsetDateTime.parse("2027-04-20T15:00:00Z");
        AccessTokenDataBinder.AccessTokenInfo token = binder.createWithExpiration(
                Optional.empty(),
                "alice",
                Map.of(),
                "authorities",
                false,
                expiration);

        assertNotNull(token.jwt());
        assertEquals(expiration, token.expiration());
        assertEquals(
                expiration.toInstant().toEpochMilli(),
                SignedJWT.parse(token.jwt()).getJWTClaimsSet().getExpirationTime().toInstant().toEpochMilli());
    }
}
