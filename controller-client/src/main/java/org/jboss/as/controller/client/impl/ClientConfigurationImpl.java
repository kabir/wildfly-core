/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.controller.client.impl;

import java.util.Map;
import java.util.concurrent.ExecutorService;

import javax.net.ssl.SSLContext;

import org.jboss.as.controller.client.ModelControllerClientConfiguration;
import org.wildfly.security.auth.client.AuthenticationContext;


/**
 * @author Emanuel Muckenhuber
 */
public class ClientConfigurationImpl implements ModelControllerClientConfiguration {

    private static final int DEFAULT_CONNECTION_TIMEOUT = 5000;
    private final String address;
    private final String clientBindAddress;
    private final int port;
    private final AuthenticationContext authenticationContext;
    private final Map<String, String> saslOptions;
    private final SSLContext sslContext;
    private final ExecutorService executorService;
    private final String protocol;
    private final boolean shutdownExecutor;
    private final int connectionTimeout;

    public ClientConfigurationImpl(String address, int port, AuthenticationContext authenticationContext,
                               Map<String, String> saslOptions, SSLContext sslContext, ExecutorService executorService,
                               boolean shutdownExecutor, final int connectionTimeout, final String protocol, String clientBindAddress) {
        this.address = address;
        this.port = port;
        this.authenticationContext = authenticationContext;
        this.saslOptions = saslOptions;
        this.sslContext = sslContext;
        this.executorService = executorService;
        this.shutdownExecutor = shutdownExecutor;
        this.protocol = protocol;
        this.clientBindAddress = clientBindAddress;
        this.connectionTimeout = connectionTimeout > 0 ? connectionTimeout : DEFAULT_CONNECTION_TIMEOUT;
    }

    @Override
    public String getHost() {
        return address;
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public String getProtocol() {
        return protocol;
    }


    @Override
    public AuthenticationContext getAuthenticationContext() {
        return authenticationContext;
    }

    @Override
    public Map<String, String> getSaslOptions() {
        return saslOptions;
    }

    @Override
    public SSLContext getSSLContext() {
        return sslContext;
    }

    @Override
    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    @Override
    public ExecutorService getExecutor() {
        return executorService;
    }

    @Override
    public void close() {
        if(shutdownExecutor && executorService != null) {
            executorService.shutdown();
        }
    }

    @Override
    public String getClientBindAddress() {
        return clientBindAddress;
    }
}
