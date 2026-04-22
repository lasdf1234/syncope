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
package org.apache.syncope.ext.scimv2.cxf;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Optional;
import org.apache.cxf.bus.spring.SpringBus;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.ext.logging.LoggingFeature;
import org.apache.cxf.ext.logging.LoggingInInterceptor;
import org.apache.cxf.ext.logging.LoggingOutInterceptor;
import org.apache.cxf.jaxrs.validation.JAXRSBeanValidationInInterceptor;
import org.apache.cxf.transport.common.gzip.GZIPInInterceptor;
import org.apache.cxf.transport.common.gzip.GZIPOutInterceptor;
import org.apache.syncope.ext.scimv2.api.data.ListResponse;
import org.apache.syncope.ext.scimv2.api.data.ResourceType;
import org.apache.syncope.ext.scimv2.api.data.SCIMAnyObject;
import org.apache.syncope.ext.scimv2.api.data.SCIMGroup;
import org.apache.syncope.ext.scimv2.api.data.SCIMPatchOp;
import org.apache.syncope.ext.scimv2.api.data.SCIMSearchRequest;
import org.apache.syncope.ext.scimv2.api.data.SCIMUser;
import org.apache.syncope.ext.scimv2.api.data.ServiceProviderConfig;
import org.apache.syncope.ext.scimv2.api.service.SCIMAnyObjectService;
import org.apache.syncope.ext.scimv2.api.service.SCIMGroupService;
import org.apache.syncope.ext.scimv2.api.service.SCIMService;
import org.apache.syncope.ext.scimv2.api.service.SCIMUserService;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;

class SCIMv2RESTCXFContextTest {

    @Test
    void scimv2ContainerAddsLoggingInterceptorsWhenFeatureIsPresent() {
        SCIMv2RESTCXFContext context = new SCIMv2RESTCXFContext();
        StaticApplicationContext applicationContext = new StaticApplicationContext();
        SpringBus bus = new SpringBus();
        bus.setApplicationContext(applicationContext);

        Server server = context.scimv2Container(
                new StubSCIMService(),
                new StubSCIMGroupService(),
                new StubSCIMUserService(),
                new StubSCIMAnyObjectService(),
                new GZIPInInterceptor(),
                new GZIPOutInterceptor(),
                new JAXRSBeanValidationInInterceptor(),
                context.scimJacksonJsonProvider(),
                context.scimExceptionMapper(),
                context.scimAddETagFilter(),
                Optional.of(new LoggingFeature()),
                bus,
                applicationContext);
        try {
            assertNotNull(server);
            assertNotNull(server.getEndpoint().getInInterceptors().stream().
                    filter(LoggingInInterceptor.class::isInstance).findFirst().orElse(null));
            assertNotNull(server.getEndpoint().getOutInterceptors().stream().
                    filter(LoggingOutInterceptor.class::isInstance).findFirst().orElse(null));
        } finally {
            server.stop();
            server.destroy();
            bus.shutdown(true);
            applicationContext.close();
        }
    }

    private static final class StubSCIMService implements SCIMService {

        @Override
        public ServiceProviderConfig serviceProviderConfig() {
            return null;
        }

        @Override
        public List<ResourceType> resourceTypes() {
            return List.of();
        }

        @Override
        public ResourceType resourceType(final String type) {
            return null;
        }

        @Override
        public Response schemas() {
            return Response.ok().build();
        }

        @Override
        public Response schema(final String schema) {
            return Response.ok().build();
        }
    }

    private static final class StubSCIMUserService implements SCIMUserService {

        @Override
        public SCIMUser get(final String id, final String attributes, final String excludedAttributes) {
            return null;
        }

        @Override
        public ListResponse<SCIMUser> search(
                final String attributes,
                final String excludedAttributes,
                final String filter,
                final String sortBy,
                final org.apache.syncope.ext.scimv2.api.type.SortOrder sortOrder,
                final Integer startIndex,
                final Integer count) {

            return null;
        }

        @Override
        public ListResponse<SCIMUser> search(final SCIMSearchRequest request) {
            return null;
        }

        @Override
        public Response create(final SCIMUser resource) {
            return Response.ok().build();
        }

        @Override
        public Response update(final String id, final SCIMPatchOp patch) {
            return Response.ok().build();
        }

        @Override
        public Response replace(final String id, final SCIMUser resource) {
            return Response.ok().build();
        }

        @Override
        public Response delete(final String id) {
            return Response.noContent().build();
        }
    }

    private static final class StubSCIMGroupService implements SCIMGroupService {

        @Override
        public SCIMGroup get(final String id, final String attributes, final String excludedAttributes) {
            return null;
        }

        @Override
        public ListResponse<SCIMGroup> search(
                final String attributes,
                final String excludedAttributes,
                final String filter,
                final String sortBy,
                final org.apache.syncope.ext.scimv2.api.type.SortOrder sortOrder,
                final Integer startIndex,
                final Integer count) {

            return null;
        }

        @Override
        public ListResponse<SCIMGroup> search(final SCIMSearchRequest request) {
            return null;
        }

        @Override
        public Response create(final SCIMGroup resource) {
            return Response.ok().build();
        }

        @Override
        public Response update(final String id, final SCIMPatchOp patch) {
            return Response.ok().build();
        }

        @Override
        public Response replace(final String id, final SCIMGroup resource) {
            return Response.ok().build();
        }

        @Override
        public Response delete(final String id) {
            return Response.noContent().build();
        }
    }

    private static final class StubSCIMAnyObjectService implements SCIMAnyObjectService {

        @Override
        public SCIMAnyObject get(final String id, final String attributes, final String excludedAttributes) {
            return null;
        }

        @Override
        public ListResponse<SCIMAnyObject> search(
                final String attributes,
                final String excludedAttributes,
                final String filter,
                final String sortBy,
                final org.apache.syncope.ext.scimv2.api.type.SortOrder sortOrder,
                final Integer startIndex,
                final Integer count) {

            return null;
        }

        @Override
        public ListResponse<SCIMAnyObject> search(final SCIMSearchRequest request) {
            return null;
        }

        @Override
        public Response create(final SCIMAnyObject resource) {
            return Response.ok().build();
        }

        @Override
        public Response update(final String id, final SCIMPatchOp patch) {
            return Response.ok().build();
        }

        @Override
        public Response replace(final String id, final SCIMAnyObject resource) {
            return Response.ok().build();
        }

        @Override
        public Response delete(final String id) {
            return Response.noContent().build();
        }
    }
}
