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
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.ws.rs.core.Response
import net.tirasa.connid.bundles.rest.RestScriptHelper
import org.apache.cxf.jaxrs.client.WebClient
import org.identityconnectors.framework.common.objects.OperationOptions

log.info("Entering " + action + " Script, objectClass=" + objectClass);

WebClient webClient = client;
ObjectMapper mapper = new ObjectMapper();
def result = [];

switch (objectClass) {
case "__ACCOUNT__":
    webClient.path("/users");
    webClient.accept("application/vnd.gravitino.v1+json");

    log.ok("Sending GET to {0}", webClient.getCurrentURI().toASCIIString());

    Response response = webClient.get();

    log.ok("List users response: {0}", response.getStatus());
    RestScriptHelper.failIfNotOk(response, "List users failed:");

    def body = mapper.readValue(response.readEntity(String.class), Map.class);
    def names = body.get("names") ?: [];
    names.each { name ->
        result.add([__UID__: name, __NAME__: name, name: name]);
    }
    break

case "__GROUP__":
    webClient.path("/groups");
    webClient.accept("application/vnd.gravitino.v1+json");

    log.ok("Sending GET to {0}", webClient.getCurrentURI().toASCIIString());

    Response response = webClient.get();

    log.ok("List groups response: {0}", response.getStatus());
    RestScriptHelper.failIfNotOk(response, "List groups failed:");

    def body = mapper.readValue(response.readEntity(String.class), Map.class);
    def names = body.get("names") ?: [];
    names.each { name ->
        result.add([__UID__: name, __NAME__: name, name: name]);
    }
    break

default:
    log.warn("Unsupported objectClass for SEARCH: {0}", objectClass);
}

result.add([(OperationOptions.OP_PAGED_RESULTS_COOKIE): null]);

return result;
