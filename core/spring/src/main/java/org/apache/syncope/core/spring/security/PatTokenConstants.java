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

public final class PatTokenConstants {

    public static final String ISSUER = "ApacheSyncope-PAT";

    public static final String CLAIM_PAT_NAME = "pat_name";

    public static final long DEFAULT_LIFETIME_DAYS = 365L;

    public static final long MIN_LIFETIME_DAYS = 1L;

    /** Maximum PAT lifetime aligned with common cloud PAT policies (e.g. Azure DevOps, GitHub). */
    public static final long MAX_LIFETIME_DAYS = 365L;

    private PatTokenConstants() {
        // static constants only
    }
}
