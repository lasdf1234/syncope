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
package org.apache.syncope.core.spring.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.jca.JCAContext;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWTClaimsSet;
import com.fasterxml.jackson.core.type.TypeReference;
import com.nimbusds.jose.JWSAlgorithm;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.apache.syncope.common.lib.types.CipherAlgorithm;
import org.apache.syncope.core.persistence.api.EncryptorManager;
import org.apache.syncope.core.persistence.api.dao.PersonalAccessTokenDAO;
import org.apache.syncope.core.persistence.api.dao.UserDAO;
import org.apache.syncope.core.persistence.api.entity.PersonalAccessToken;
import org.apache.syncope.core.persistence.api.entity.user.User;
import org.apache.syncope.core.provisioning.api.serialization.POJOHelper;
import org.apache.syncope.core.spring.security.jws.AccessTokenJWSVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.transaction.annotation.Transactional;

public class PatJWTSSOProvider implements JWTSSOProvider {

    protected static final Logger LOG = LoggerFactory.getLogger(PatJWTSSOProvider.class);

    protected final AccessTokenJWSVerifier delegate;

    protected final EncryptorManager encryptorManager;

    protected final UserDAO userDAO;

    protected final PersonalAccessTokenDAO personalAccessTokenDAO;

    protected final SecurityProperties securityProperties;

    public PatJWTSSOProvider(
            final AccessTokenJWSVerifier accessTokenJWSVerifier,
            final EncryptorManager encryptorManager,
            final UserDAO userDAO,
            final PersonalAccessTokenDAO personalAccessTokenDAO,
            final SecurityProperties securityProperties) {

        this.delegate = accessTokenJWSVerifier;
        this.encryptorManager = encryptorManager;
        this.userDAO = userDAO;
        this.personalAccessTokenDAO = personalAccessTokenDAO;
        this.securityProperties = securityProperties;
    }

    @Override
    public String getIssuer() {
        return PatTokenConstants.ISSUER;
    }

    @Override
    public Set<JWSAlgorithm> supportedJWSAlgorithms() {
        return delegate.supportedJWSAlgorithms();
    }

    @Override
    public JCAContext getJCAContext() {
        return delegate.getJCAContext();
    }

    @Override
    public boolean verify(
            final JWSHeader header,
            final byte[] signingInput,
            final Base64URL signature) throws JOSEException {

        return delegate.verify(header, signingInput, signature);
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<ResolvedClaims> resolve(final JWTClaimsSet jwtClaims) {
        PersonalAccessToken pat = personalAccessTokenDAO.findById(jwtClaims.getJWTID()).
                orElseThrow(() -> new AuthenticationCredentialsNotFoundException(
                "Could not find a Personal Access Token for JWT " + jwtClaims.getJWTID()));

        OffsetDateTime now = OffsetDateTime.now();
        if (pat.getExpirationTime() != null && pat.getExpirationTime().isBefore(now)) {
            throw new AuthenticationCredentialsNotFoundException("PAT " + jwtClaims.getJWTID() + " is expired");
        }

        if (!jwtClaims.getSubject().equals(pat.getOwner())) {
            throw new AuthenticationCredentialsNotFoundException(
                    "PAT subject does not match registered owner for " + jwtClaims.getJWTID());
        }

        Set<SyncopeGrantedAuthority> authorities = new HashSet<>();
        if (securityProperties.getAdminUser().equals(pat.getOwner())) {
            // admin PAT: authorities resolved later in AuthDataAccessor
        } else if (pat.getAuthorities() != null) {
            try {
                authorities.addAll(POJOHelper.deserialize(
                        encryptorManager.getInstance().decode(pat.getAuthorities(), CipherAlgorithm.AES),
                        new TypeReference<>() {
                        }));
            } catch (Throwable t) {
                LOG.error("Could not read stored PAT authorities", t);
            }
        }

        return userDAO.findByUsername(jwtClaims.getSubject()).map(user -> {
            validateUser(user);
            LOG.debug("PAT {} resolved to User {}", jwtClaims.getJWTID(), user.getUsername());
            return new ResolvedClaims(user, authorities);
        });
    }

    protected void validateUser(final User user) {
        if (Boolean.TRUE.equals(user.isSuspended())) {
            throw new org.springframework.security.authentication.DisabledException(
                    "User " + user.getUsername() + " is suspended");
        }
    }
}
