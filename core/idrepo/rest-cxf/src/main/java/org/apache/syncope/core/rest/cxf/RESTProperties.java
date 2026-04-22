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

import org.apache.syncope.core.provisioning.java.ExecutorProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties("rest")
public class RESTProperties {

    @NestedConfigurationProperty
    private final ExecutorProperties batchExecutor = new ExecutorProperties();

    @NestedConfigurationProperty
    private final LoggingProperties logging = new LoggingProperties();

    public ExecutorProperties getBatchExecutor() {
        return batchExecutor;
    }

    public LoggingProperties getLogging() {
        return logging;
    }

    public static class LoggingProperties {

        private boolean enabled;

        private boolean pretty = true;

        private boolean verbose = true;

        private int limit = Integer.MAX_VALUE;

        private boolean logBinary;

        private boolean logMultipart = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(final boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isPretty() {
            return pretty;
        }

        public void setPretty(final boolean pretty) {
            this.pretty = pretty;
        }

        public boolean isVerbose() {
            return verbose;
        }

        public void setVerbose(final boolean verbose) {
            this.verbose = verbose;
        }

        public int getLimit() {
            return limit;
        }

        public void setLimit(final int limit) {
            this.limit = limit;
        }

        public boolean isLogBinary() {
            return logBinary;
        }

        public void setLogBinary(final boolean logBinary) {
            this.logBinary = logBinary;
        }

        public boolean isLogMultipart() {
            return logMultipart;
        }

        public void setLogMultipart(final boolean logMultipart) {
            this.logMultipart = logMultipart;
        }
    }
}
