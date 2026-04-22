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
package org.apache.syncope.core.rest.cxf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.List;
import org.apache.cxf.bus.spring.SpringBus;
import org.apache.cxf.ext.logging.LoggingFeature;
import org.apache.cxf.ext.logging.LoggingInInterceptor;
import org.apache.cxf.ext.logging.LoggingOutInterceptor;
import org.apache.cxf.interceptor.Interceptor;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.message.Message;
import org.junit.jupiter.api.Test;

class IdRepoRESTCXFContextTest {

    @Test
    void loggingPropertiesDefaultsAreSafe() {
        RESTProperties.LoggingProperties logging = new RESTProperties().getLogging();

        assertFalse(logging.isEnabled());
        assertTrue(logging.isPretty());
        assertTrue(logging.isVerbose());
        assertEquals(Integer.MAX_VALUE, logging.getLimit());
        assertFalse(logging.isLogBinary());
        assertTrue(logging.isLogMultipart());
    }

    @Test
    void requestResponseLoggingFeatureAppliesConfiguredInterceptors() throws Exception {
        RESTProperties props = new RESTProperties();
        props.getLogging().setPretty(false);
        props.getLogging().setVerbose(true);
        props.getLogging().setLimit(512);
        props.getLogging().setLogBinary(true);
        props.getLogging().setLogMultipart(false);

        LoggingFeature feature = new IdRepoRESTCXFContext().requestResponseLoggingFeature(props);
        assertNotNull(feature);

        JAXRSServerFactoryBean provider = new JAXRSServerFactoryBean();
        provider.setBus(new SpringBus());
        feature.initialize(provider, provider.getBus());

        LoggingInInterceptor in = find(provider.getInInterceptors(), LoggingInInterceptor.class);
        LoggingOutInterceptor out = find(provider.getOutInterceptors(), LoggingOutInterceptor.class);

        assertEquals(512, in.getLimit());
        assertEquals(512, out.getLimit());
        assertTrue((Boolean) readField(in, "logBinary"));
        assertFalse((Boolean) readField(in, "logMultipart"));
        assertNotNull(readField(feature, "delegate"));
    }

    private static <T extends Interceptor<? extends Message>> T find(
            final List<Interceptor<? extends Message>> interceptors,
            final Class<T> type) {

        return interceptors.stream().filter(type::isInstance).map(type::cast).findFirst().orElseThrow();
    }
    private static Object readField(final Object target, final String name) throws Exception {
        Field field = findField(target.getClass(), name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Field findField(final Class<?> type, final String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
