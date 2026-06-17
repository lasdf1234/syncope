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
package org.apache.syncope.core.persistence.jpa.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

class MasterContentRecoveryImplementationsTest {

    @Test
    void masterContentContainsOrphanCleanupInboundActions() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
                Files.newInputStream(Path.of("src/main/resources/domains/MasterContent.xml")));

        assertEquals(
                "org.apache.syncope.core.provisioning.java.pushpull.OrphanCleanupInboundActions",
                implementationBody(doc, "OrphanCleanupInboundActions"));
        assertEquals("INBOUND_ACTIONS", xpathAttr(doc, "OrphanCleanupInboundActions", "type"));
        assertEquals("JAVA", xpathAttr(doc, "OrphanCleanupInboundActions", "engine"));
    }

    private static String implementationBody(final Document doc, final String id) throws Exception {
        Node node = (Node) XPathFactory.newInstance().newXPath().evaluate(
                "/dataset/Implementation[@id='" + id + "']/@body",
                doc,
                XPathConstants.NODE);
        assertNotNull(node, "Implementation " + id);
        return node.getTextContent();
    }

    private static String xpathAttr(final Document doc, final String id, final String attr) throws Exception {
        Node node = (Node) XPathFactory.newInstance().newXPath().evaluate(
                "/dataset/Implementation[@id='" + id + "']/@" + attr,
                doc,
                XPathConstants.NODE);
        assertNotNull(node, id + "@" + attr);
        return node.getTextContent();
    }
}
