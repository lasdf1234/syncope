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

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.syncope.common.lib.SyncopeClientException;
import org.apache.syncope.common.lib.to.AccessTokenTO;
import org.apache.syncope.common.lib.to.PersonalAccessTokenCreateTO;
import org.apache.syncope.common.lib.to.PersonalAccessTokenTO;
import org.apache.syncope.common.lib.types.CipherAlgorithm;
import org.apache.syncope.common.lib.types.ClientExceptionType;
import org.apache.syncope.common.lib.types.IdRepoEntitlement;
import org.apache.syncope.core.persistence.api.EncryptorManager;
import org.apache.syncope.core.persistence.api.dao.AccessTokenDAO;
import org.apache.syncope.core.persistence.api.dao.NotFoundException;
import org.apache.syncope.core.persistence.api.dao.PersonalAccessTokenDAO;
import org.apache.syncope.core.persistence.api.entity.AccessToken;
import org.apache.syncope.core.persistence.api.entity.PersonalAccessToken;
import org.apache.syncope.core.provisioning.api.data.AccessTokenDataBinder;
import org.apache.syncope.core.provisioning.api.data.PersonalAccessTokenDataBinder;
import org.apache.syncope.core.provisioning.api.serialization.POJOHelper;
import org.apache.syncope.core.spring.security.AuthContextUtils;
import org.apache.syncope.core.spring.security.PatTokenConstants;
import org.apache.syncope.core.spring.security.SecurityProperties;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;

public class AccessTokenLogic extends AbstractTransactionalLogic<AccessTokenTO> {

    protected final SecurityProperties securityProperties;

    protected final EncryptorManager encryptorManager;

    protected final AccessTokenDataBinder binder;

    protected final PersonalAccessTokenDataBinder patBinder;

    protected final AccessTokenDAO accessTokenDAO;

    protected final PersonalAccessTokenDAO personalAccessTokenDAO;

    public AccessTokenLogic(
            final SecurityProperties securityProperties,
            final EncryptorManager encryptorManager,
            final AccessTokenDataBinder binder,
            final PersonalAccessTokenDataBinder patBinder,
            final AccessTokenDAO accessTokenDAO,
            final PersonalAccessTokenDAO personalAccessTokenDAO) {

        this.securityProperties = securityProperties;
        this.encryptorManager = encryptorManager;
        this.binder = binder;
        this.patBinder = patBinder;
        this.accessTokenDAO = accessTokenDAO;
        this.personalAccessTokenDAO = personalAccessTokenDAO;
    }

    protected String getAuthorities() {
        String authorities = null;
        try {
            authorities = encryptorManager.getInstance().
                    encode(POJOHelper.serialize(AuthContextUtils.getAuthorities()), CipherAlgorithm.AES);
        } catch (Exception e) {
            LOG.error("Could not fetch authorities", e);
        }

        return authorities;
    }

    protected long resolveLifetimeDays(final PersonalAccessTokenCreateTO input) {
        long days = input == null || input.getDays() == null
                ? PatTokenConstants.DEFAULT_LIFETIME_DAYS
                : input.getDays();
        if (days < PatTokenConstants.MIN_LIFETIME_DAYS || days > PatTokenConstants.MAX_LIFETIME_DAYS) {
            SyncopeClientException sce = SyncopeClientException.build(ClientExceptionType.InvalidRequest);
            sce.getElements().add("PAT lifetime in days must be between "
                    + PatTokenConstants.MIN_LIFETIME_DAYS + " and " + PatTokenConstants.MAX_LIFETIME_DAYS);
            throw sce;
        }
        return days;
    }

    protected void rejectAnonymousUser() {
        if (securityProperties.getAnonymousUser().equals(AuthContextUtils.getUsername())) {
            SyncopeClientException sce = SyncopeClientException.build(ClientExceptionType.InvalidRequest);
            sce.getElements().add(securityProperties.getAnonymousUser() + " cannot be granted an access token");
            throw sce;
        }
    }

    @PreAuthorize("isAuthenticated()")
    public AccessTokenDataBinder.AccessTokenInfo login() {
        rejectAnonymousUser();

        return binder.create(
                Optional.empty(),
                AuthContextUtils.getUsername(),
                Map.of(),
                getAuthorities(),
                false);
    }

    @PreAuthorize("isAuthenticated()")
    public AccessTokenDataBinder.AccessTokenInfo token(final PersonalAccessTokenCreateTO input) {
        rejectAnonymousUser();

        String name = input == null ? null : input.getName();
        long days = resolveLifetimeDays(input);

        return patBinder.create(
                AuthContextUtils.getUsername(),
                name,
                days,
                Map.of(),
                getAuthorities());
    }

    @PreAuthorize("isAuthenticated()")
    public AccessTokenDataBinder.AccessTokenInfo refresh() {
        AccessToken accessToken = accessTokenDAO.findByOwner(AuthContextUtils.getUsername()).
                orElseThrow(() -> {
                    SyncopeClientException sce = SyncopeClientException.build(ClientExceptionType.InvalidRequest);
                    sce.getElements().add("Session access token not found; personal access tokens cannot be refreshed");
                    return sce;
                });

        return binder.update(accessToken, getAuthorities());
    }

    @PreAuthorize("isAuthenticated()")
    public void logout() {
        AccessToken accessToken = accessTokenDAO.findByOwner(AuthContextUtils.getUsername()).
                orElseThrow(() -> new NotFoundException("AccessToken for " + AuthContextUtils.getUsername()));

        delete(accessToken.getKey());
    }

    @PreAuthorize("hasRole('" + IdRepoEntitlement.ACCESS_TOKEN_LIST + "')")
    public Page<AccessTokenTO> list(final Pageable pageable) {
        return accessTokenDAO.findAll(pageable).map(binder::getAccessTokenTO);
    }

    @PreAuthorize("hasRole('" + IdRepoEntitlement.ACCESS_TOKEN_DELETE + "')")
    public void delete(final String key) {
        accessTokenDAO.deleteById(key);
    }

    @PreAuthorize("isAuthenticated()")
    public List<PersonalAccessTokenTO> listPat() {
        return personalAccessTokenDAO.findByOwner(AuthContextUtils.getUsername()).stream().
                map(patBinder::getPersonalAccessTokenTO).
                toList();
    }

    @PreAuthorize("isAuthenticated()")
    public void deletePat(final String key) {
        PersonalAccessToken pat = personalAccessTokenDAO.findById(key).
                orElseThrow(() -> new NotFoundException("PersonalAccessToken " + key));

        boolean isOwner = AuthContextUtils.getUsername().equals(pat.getOwner());
        boolean isAdmin = AuthContextUtils.getAuthorities().stream().
                anyMatch(a -> IdRepoEntitlement.ACCESS_TOKEN_DELETE.equals(a.getAuthority()));

        if (!isOwner && !isAdmin) {
            SyncopeClientException sce = SyncopeClientException.build(ClientExceptionType.DelegatedAdministration);
            sce.getElements().add("Not allowed to revoke PAT " + key);
            throw sce;
        }

        personalAccessTokenDAO.deleteById(key);
    }

    @Override
    protected AccessTokenTO resolveReference(final Method method, final Object... args)
            throws UnresolvedReferenceException {

        throw new UnresolvedReferenceException();
    }
}
