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
import jakarta.ws.rs.core.Response
import net.tirasa.connid.bundles.rest.RestScriptHelper
import org.apache.cxf.jaxrs.client.WebClient
import org.identityconnectors.framework.common.objects.OperationOptions

log.info("Entering " + action + " Script");

WebClient webClient = client;

def result = [];

String uidValue = null;
if (query != null) {
  log.info("Query: {0}", query.toString());
  uidValue = query.get("right")?.toString();
}

switch (objectClass) {
case "__ACCOUNT__":
  if (uidValue != null) {
    webClient.path("/users/" + uidValue);
    webClient.accept("application/vnd.gravitino.v1+json");

    log.ok("Sending GET to {0}", webClient.getCurrentURI().toASCIIString());

    Response response = webClient.get();

    int status = response.getStatus();
    log.ok("Get user response: {0}", status);

    if (status == 200) {
      result.add([__UID__: uidValue, __NAME__: uidValue, name: uidValue]);
    } else if (status == 404) {
      log.warn("User {0} not found in Gravitino (status=404)", uidValue);
    } else {
      RestScriptHelper.fail(response, "Get user " + uidValue + " failed:");
    }
  }
  break

case "__GROUP__":
  if (uidValue != null) {
    webClient.path("/groups/" + uidValue);
    webClient.accept("application/vnd.gravitino.v1+json");

    log.ok("Sending GET to {0}", webClient.getCurrentURI().toASCIIString());

    Response response = webClient.get();

    int status = response.getStatus();
    log.ok("Get group response: {0}", status);

    if (status == 200) {
      result.add([__UID__: uidValue, __NAME__: uidValue, name: uidValue]);
    } else if (status == 404) {
      log.warn("Group {0} not found in Gravitino (status=404)", uidValue);
    } else {
      RestScriptHelper.fail(response, "Get group " + uidValue + " failed:");
    }
  }
  break

default:
  log.warn("Unsupported objectClass for SEARCH: {0}", objectClass);
}

result.add([(OperationOptions.OP_PAGED_RESULTS_COOKIE): null]);

return result;
