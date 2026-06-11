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
package org.apache.syncope.core.provisioning.java.data;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.syncope.common.lib.SyncopeClientException;
import org.apache.syncope.common.lib.to.PersonalAccessTokenTO;
import org.apache.syncope.common.lib.types.ClientExceptionType;
import org.apache.syncope.core.persistence.api.dao.PersonalAccessTokenDAO;
import org.apache.syncope.core.persistence.api.entity.EntityFactory;
import org.apache.syncope.core.persistence.api.entity.PersonalAccessToken;
import org.apache.syncope.core.provisioning.api.data.AccessTokenDataBinder;
import org.apache.syncope.core.provisioning.api.data.PersonalAccessTokenDataBinder;
import org.apache.syncope.core.spring.security.DefaultCredentialChecker;
import org.apache.syncope.core.spring.security.PatTokenConstants;
import org.apache.syncope.core.spring.security.SecureRandomUtils;
import org.apache.syncope.core.spring.security.SecurityProperties;
import org.apache.syncope.core.spring.security.jws.AccessTokenJWSSigner;

public class PersonalAccessTokenDataBinderImpl implements PersonalAccessTokenDataBinder {

    protected final SecurityProperties securityProperties;

    protected final AccessTokenJWSSigner jwsSigner;

    protected final PersonalAccessTokenDAO personalAccessTokenDAO;

    protected final EntityFactory entityFactory;

    protected final DefaultCredentialChecker credentialChecker;

    public PersonalAccessTokenDataBinderImpl(
            final SecurityProperties securityProperties,
            final AccessTokenJWSSigner jwsSigner,
            final PersonalAccessTokenDAO personalAccessTokenDAO,
            final EntityFactory entityFactory,
            final DefaultCredentialChecker credentialChecker) {

        this.securityProperties = securityProperties;
        this.jwsSigner = jwsSigner;
        this.personalAccessTokenDAO = personalAccessTokenDAO;
        this.entityFactory = entityFactory;
        this.credentialChecker = credentialChecker;
    }

    protected AccessTokenDataBinder.AccessTokenInfo signJWT(
            final String tokenId,
            final String subject,
            final OffsetDateTime expiration,
            final Map<String, Object> claims) {

        credentialChecker.checkIsDefaultJWSKeyInUse();

        OffsetDateTime currentTime = OffsetDateTime.now();
        Date issueTime = new Date(currentTime.toInstant().toEpochMilli());

        JWTClaimsSet.Builder claimsSet = new JWTClaimsSet.Builder().
                jwtID(tokenId).
                subject(subject).
                issuer(PatTokenConstants.ISSUER).
                issueTime(issueTime).
                expirationTime(new Date(expiration.toInstant().toEpochMilli())).
                notBeforeTime(issueTime);
        claims.forEach(claimsSet::claim);

        SignedJWT jwt = new SignedJWT(new JWSHeader(jwsSigner.getJwsAlgorithm()), claimsSet.build());
        try {
            jwt.sign(jwsSigner);
        } catch (JOSEException e) {
            SyncopeClientException sce = SyncopeClientException.build(ClientExceptionType.InvalidAccessToken);
            sce.getElements().add(e.getMessage());
            throw sce;
        }
        return new AccessTokenDataBinder.AccessTokenInfo(jwt.serialize(), expiration);
    }

    @Override
    public AccessTokenDataBinder.AccessTokenInfo create(
            final String subject,
            final String name,
            final long lifetimeDays,
            final Map<String, Object> claims,
            final String authorities) {

        String jti = SecureRandomUtils.generateRandomUUID().toString();
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime expiration = now.plusDays(lifetimeDays);
        String tokenName = StringUtils.isNotBlank(name) ? name : "PAT-" + jti.substring(0, 8);

        Map<String, Object> enrichedClaims = new java.util.HashMap<>(claims);
        enrichedClaims.putIfAbsent(PatTokenConstants.CLAIM_PAT_NAME, tokenName);

        AccessTokenDataBinder.AccessTokenInfo generated = signJWT(jti, subject, expiration, enrichedClaims);

        PersonalAccessToken pat = entityFactory.newEntity(PersonalAccessToken.class);
        pat.setKey(jti);
        pat.setOwner(subject);
        pat.setName(tokenName);
        pat.setCreatedTime(now);
        pat.setExpirationTime(expiration);

        if (!securityProperties.getAdminUser().equals(subject)) {
            pat.setAuthorities(authorities);
        }

        personalAccessTokenDAO.save(pat);

        return generated;
    }

    @Override
    public PersonalAccessTokenTO getPersonalAccessTokenTO(final PersonalAccessToken personalAccessToken) {
        PersonalAccessTokenTO to = new PersonalAccessTokenTO();
        to.setKey(personalAccessToken.getKey());
        to.setName(personalAccessToken.getName());
        to.setOwner(personalAccessToken.getOwner());
        to.setCreatedTime(personalAccessToken.getCreatedTime());
        to.setExpirationTime(personalAccessToken.getExpirationTime());
        to.setLastUsedTime(personalAccessToken.getLastUsedTime());
        return to;
    }
}
