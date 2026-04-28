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
import org.apache.cxf.jaxrs.client.WebClient
import org.identityconnectors.framework.common.objects.OperationOptions

// Parameters:
// The connector sends us the following:
// client:      CXF WebClient
// action:      String corresponding to the action ("SEARCH" here)
// log:         a handler to the Log facility
// objectClass: a String describing the Object class (__ACCOUNT__ / __GROUP__ / other)
// query:       a Map describing the filter: { conditionType, left, right }
//              or null to fetch all
// options:     a handler to the OperationOptions Map
//
// Returns: A List of Maps. Each Map must contain __UID__ and __NAME__.
//          Last element must be the pagination cookie Map.

log.info("Entering " + action + " Script");

WebClient webClient = client;

def result = [];

// Extract the value to look up from the query filter
String uidValue = null;
if (query != null) {
  log.info("Query: {0}", query.toString());
  uidValue = query.get("right")?.toString();
}

switch (objectClass) {
case "__ACCOUNT__":
  if (uidValue != null) {
    // GET /users/{user}
    webClient.path("/users/" + uidValue);
    webClient.accept("application/vnd.gravitino.v1+json");

    log.ok("Sending GET to {0}", webClient.getCurrentURI().toASCIIString());

    Response response = webClient.get();

    log.ok("Get user response: {0}", response.getStatus());

    if (response.getStatus() == 200) {
      result.add([__UID__: uidValue, __NAME__: uidValue, name: uidValue]);
    } else {
      log.warn("User {0} not found in Gravitino (status={1})", uidValue, response.getStatus());
    }
  }
  break

case "__GROUP__":
  if (uidValue != null) {
    // GET /groups/{group}
    webClient.path("/groups/" + uidValue);
    webClient.accept("application/vnd.gravitino.v1+json");

    log.ok("Sending GET to {0}", webClient.getCurrentURI().toASCIIString());

    Response response = webClient.get();

    log.ok("Get group response: {0}", response.getStatus());

    if (response.getStatus() == 200) {
      result.add([__UID__: uidValue, __NAME__: uidValue, name: uidValue]);
    } else {
      log.warn("Group {0} not found in Gravitino (status={1})", uidValue, response.getStatus());
    }
  }
  break

default:
  log.warn("Unsupported objectClass for SEARCH: {0}", objectClass);
}

// Required: pagination cookie entry as last element
result.add([(OperationOptions.OP_PAGED_RESULTS_COOKIE): null]);

return result;
