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
package org.apache.syncope.core.rest.cxf.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import org.apache.syncope.common.rest.api.RESTHeaders;
import org.apache.syncope.core.logic.AccessTokenLogic;
import org.apache.syncope.core.provisioning.api.data.AccessTokenDataBinder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccessTokenServiceTest {

    @Mock
    private AccessTokenLogic logic;

    @InjectMocks
    private AccessTokenServiceImpl service;

    @Test
    void token() {
        OffsetDateTime expiration = OffsetDateTime.parse("2027-04-20T12:00:00Z");
        when(logic.token()).thenReturn(new AccessTokenDataBinder.AccessTokenInfo("jwt-token", expiration));

        try (Response response = service.token()) {
            assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response.getStatus());
            assertEquals("jwt-token", response.getHeaderString(RESTHeaders.TOKEN));
            assertEquals(
                    DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(expiration),
                    response.getHeaderString(RESTHeaders.TOKEN_EXPIRE));
        }

        verify(logic).token();
        verifyNoMoreInteractions(logic);
    }
}
