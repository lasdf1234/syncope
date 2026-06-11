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
package org.apache.syncope.common.lib.to;

import java.time.OffsetDateTime;

public class PersonalAccessTokenTO implements EntityTO {

    private static final long serialVersionUID = 1L;

    private String key;

    private String name;

    private String owner;

    private OffsetDateTime createdTime;

    private OffsetDateTime expirationTime;

    private OffsetDateTime lastUsedTime;

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public void setKey(final String key) {
        this.key = key;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(final String owner) {
        this.owner = owner;
    }

    public OffsetDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(final OffsetDateTime createdTime) {
        this.createdTime = createdTime;
    }

    public OffsetDateTime getExpirationTime() {
        return expirationTime;
    }

    public void setExpirationTime(final OffsetDateTime expirationTime) {
        this.expirationTime = expirationTime;
    }

    public OffsetDateTime getLastUsedTime() {
        return lastUsedTime;
    }

    public void setLastUsedTime(final OffsetDateTime lastUsedTime) {
        this.lastUsedTime = lastUsedTime;
    }
}
