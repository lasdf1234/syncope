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

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.core.Response;
import java.util.Map;

public final class RestScriptHelper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RestScriptHelper() {
    }

    public static void failIfNotOk(final Response response, final String label) {
        if (response.getStatus() != 200) {
            fail(response, label);
        }
    }

    public static void fail(final Response response, final String label) {
        int status = response.getStatus();
        String body = response.hasEntity() ? response.readEntity(String.class) : "";
        String msg = body;
        if (!body.isEmpty()) {
            try {
                Map<?, ?> json = MAPPER.readValue(body, Map.class);
                Object message = json.get("message");
                if (message != null) {
                    msg = message.toString();
                }
            } catch (Exception ignored) {
                // keep raw body
            }
        }
        throw new RuntimeException(label + " HTTP " + status + (msg.isEmpty() ? "" : " " + msg));
    }
}
