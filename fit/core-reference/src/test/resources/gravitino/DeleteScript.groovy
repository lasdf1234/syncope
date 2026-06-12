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

log.info("Entering " + action + " Script");

WebClient webClient = client;

switch (objectClass) {
case "__ACCOUNT__":
  String userName = uid;

  webClient.path("/users/" + userName);
  webClient.accept("application/vnd.gravitino.v1+json");

  log.ok("Sending DELETE to {0}", webClient.getCurrentURI().toASCIIString());

  Response response = webClient.delete();

  log.ok("Delete user response: {0} {1}", response.getStatus(), response.getHeaders());
  RestScriptHelper.failIfNotOk(response, "Delete user failed:");
  break

case "__GROUP__":
  String groupName = uid;

  webClient.path("/groups/" + groupName);
  webClient.accept("application/vnd.gravitino.v1+json");

  log.ok("Sending DELETE to {0}", webClient.getCurrentURI().toASCIIString());

  Response response = webClient.delete();

  log.ok("Delete group response: {0} {1}", response.getStatus(), response.getHeaders());
  RestScriptHelper.failIfNotOk(response, "Delete group failed:");
  break

default:
  log.warn("Unsupported objectClass for DELETE: {0}", objectClass);
}
