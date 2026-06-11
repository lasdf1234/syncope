/**
 * Copyright (C) 2016 ConnId (connid-dev@googlegroups.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.tirasa.connid.bundles.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.identityconnectors.common.security.GuardedString;
import org.junit.jupiter.api.Test;

public class RESTConnectorTokenTests {

    private static final class TestableRESTConnector extends RESTConnector {

        private void setConfiguration(final RESTConfiguration configuration) {
            this.config = configuration;
        }
    }

    private TestableRESTConnector newConnector(final RESTConfiguration configuration) {
        TestableRESTConnector connector = new TestableRESTConnector();
        connector.setConfiguration(configuration);
        return connector;
    }

    @Test
    public void buildTokenRequestBodyWithPasswordGrant() {
        RESTConfiguration configuration = new RESTConfiguration();
        configuration.setAccessTokenGrantType("password");
        configuration.setAccessTokenScope("openid");
        configuration.setClientId("gravitino-client");
        configuration.setClientSecret("secret");
        configuration.setUsername("userb");
        configuration.setPassword(new GuardedString("123456".toCharArray()));

        String body = newConnector(configuration).buildTokenRequestBody();

        assertTrue(body.startsWith("grant_type=password&"));
        assertTrue(body.contains("scope=openid"));
        assertTrue(body.contains("client_id=gravitino-client"));
        assertTrue(body.contains("client_secret=secret"));
        assertTrue(body.contains("username=userb"));
        assertTrue(body.contains("password=123456"));
    }

    @Test
    public void buildTokenRequestBodyWithClientCredentialsGrant() {
        RESTConfiguration configuration = new RESTConfiguration();
        configuration.setAccessTokenGrantType("client_credentials");
        configuration.setClientId("gravitino-client");
        configuration.setClientSecret("secret");
        configuration.setUsername("ignored");
        configuration.setPassword(new GuardedString("ignored".toCharArray()));

        String body = newConnector(configuration).buildTokenRequestBody();

        assertEquals("grant_type=client_credentials&client_id=gravitino-client&client_secret=secret", body);
        assertFalse(body.contains("username="));
        assertFalse(body.contains("password="));
    }
}
