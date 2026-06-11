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
package org.apache.syncope.core.persistence.neo4j.entity;

import java.time.OffsetDateTime;
import org.apache.syncope.core.persistence.api.entity.PersonalAccessToken;
import org.springframework.data.neo4j.core.schema.Node;

@Node(Neo4jPersonalAccessToken.NODE)
public class Neo4jPersonalAccessToken extends AbstractProvidedKeyNode implements PersonalAccessToken {

    private static final long serialVersionUID = 1L;

    public static final String NODE = "PersonalAccessToken";

    private String owner;

    private String name;

    private String authorities;

    private OffsetDateTime expirationTime;

    private OffsetDateTime createdTime;

    private OffsetDateTime lastUsedTime;

    @Override
    public String getOwner() {
        return owner;
    }

    @Override
    public void setOwner(final String owner) {
        this.owner = owner;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(final String name) {
        this.name = name;
    }

    @Override
    public String getAuthorities() {
        return authorities;
    }

    @Override
    public void setAuthorities(final String authorities) {
        this.authorities = authorities;
    }

    @Override
    public OffsetDateTime getExpirationTime() {
        return expirationTime;
    }

    @Override
    public void setExpirationTime(final OffsetDateTime expirationTime) {
        this.expirationTime = expirationTime;
    }

    @Override
    public OffsetDateTime getCreatedTime() {
        return createdTime;
    }

    @Override
    public void setCreatedTime(final OffsetDateTime createdTime) {
        this.createdTime = createdTime;
    }

    @Override
    public OffsetDateTime getLastUsedTime() {
        return lastUsedTime;
    }

    @Override
    public void setLastUsedTime(final OffsetDateTime lastUsedTime) {
        this.lastUsedTime = lastUsedTime;
    }
}
