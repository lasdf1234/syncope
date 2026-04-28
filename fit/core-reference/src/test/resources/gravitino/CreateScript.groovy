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
import com.fasterxml.jackson.databind.node.ObjectNode
import jakarta.ws.rs.core.Response
import org.apache.cxf.jaxrs.client.WebClient

// Parameters:
// The connector sends us the following:
// client : CXF WebClient
// action: String correponding to the action ("CREATE" here)
// log: a handler to the Log facility
// objectClass: a String describing the Object class (__ACCOUNT__ / __GROUP__ / other)
// id: The entry identifier (ConnId's "Name" atribute. (most often matches the uid))
// attributes: an Attribute Map, containg the <String> attribute name as a key
// and the <List> attribute value(s) as value.
// password: password string, clear text
// options: a handler to the OperationOptions Map

log.info("Entering " + action + " Script");

WebClient webClient = client;
ObjectMapper mapper = new ObjectMapper();

String key;

switch (objectClass) {
case "__ACCOUNT__":
  ObjectNode node = mapper.createObjectNode();
  String name = id;
  node.set("name", node.textNode(name));

  String payload = mapper.writeValueAsString(node);

  webClient.path("/users");
  webClient.accept("application/vnd.gravitino.v1+json");
  webClient.type("application/json");

  log.ok("Sending POST to {0} with payload {1}", webClient.getCurrentURI().toASCIIString(), payload);

  Response response = webClient.post(payload);

  log.ok("Create response: {0} {1}", response.getStatus(), response.getHeaders());

  key = name;
  break

case "__GROUP__":
  ObjectNode node = mapper.createObjectNode();
  String name = id;
  node.set("name", node.textNode(name));

  String payload = mapper.writeValueAsString(node);

  webClient.path("/groups");
  webClient.accept("application/vnd.gravitino.v1+json");
  webClient.type("application/json");

  log.ok("Sending POST to {0} with payload {1}", webClient.getCurrentURI().toASCIIString(), payload);

  Response response = webClient.post(payload);

  log.ok("Create response: {0} {1}", response.getStatus(), response.getHeaders());

  key = name;
  break

default:
  key = id;
}

return key;
