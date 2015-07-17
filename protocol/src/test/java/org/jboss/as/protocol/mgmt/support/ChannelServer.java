/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2006, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.as.protocol.mgmt.support;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.spec.InvalidKeySpecException;
import java.util.concurrent.Executor;

import javax.security.sasl.SaslServerFactory;

import org.jboss.remoting3.Channel;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.OpenListener;
import org.jboss.remoting3.Registration;
import org.jboss.remoting3.RemotingOptions;
import org.jboss.remoting3.ServiceRegistrationException;
import org.jboss.remoting3.remote.RemoteConnectionProviderFactory;
import org.jboss.remoting3.spi.NetworkServerProvider;
import org.wildfly.security.WildFlyElytronProvider;
import org.wildfly.security.auth.provider.SimpleMapBackedSecurityRealm;
import org.wildfly.security.auth.server.SecurityDomain;
import org.wildfly.security.sasl.util.ProtocolSaslServerFactory;
import org.wildfly.security.sasl.util.ServerNameSaslServerFactory;
import org.wildfly.security.sasl.util.ServiceLoaderSaslServerFactory;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.ConnectedStreamChannel;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @version $Revision: 1.1 $
 */
public class ChannelServer implements Closeable {
    private final Endpoint endpoint;
    private final Registration registration;
    private final AcceptingChannel<? extends ConnectedStreamChannel> streamServer;

    private ChannelServer(final Endpoint endpoint,
            final Registration registration,
            final AcceptingChannel<? extends ConnectedStreamChannel> streamServer) {
        this.endpoint = endpoint;
        this.registration = registration;
        this.streamServer = streamServer;
    }

    public static ChannelServer create(final Configuration configuration) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        if (configuration == null) {
            throw new IllegalArgumentException("Null configuration");
        }
        configuration.validate();

        final Endpoint endpoint = Endpoint.builder()
                .setEndpointName(configuration.getEndpointName())
                .setXnioWorkerOptions(configuration.getOptionMap())
                .build();

        Registration registration = endpoint.addConnectionProvider(configuration.getUriScheme(), new RemoteConnectionProviderFactory(), OptionMap.create(Options.SSL_ENABLED, Boolean.FALSE));

        final NetworkServerProvider networkServerProvider = endpoint.getConnectionProviderInterface(configuration.getUriScheme(), NetworkServerProvider.class);
        Security.addProvider(new WildFlyElytronProvider());
        final SecurityDomain.Builder domainBuilder = SecurityDomain.builder();
        final SimpleMapBackedSecurityRealm mainRealm = new SimpleMapBackedSecurityRealm();
        domainBuilder.addRealm(RemoteChannelPairSetup.REALM, mainRealm);
        domainBuilder.setDefaultRealmName(RemoteChannelPairSetup.REALM);
        mainRealm.setPasswordMap(RemoteChannelPairSetup.USER, RemoteChannelPairSetup.createPassword());
        SaslServerFactory saslServerFactory = new ServiceLoaderSaslServerFactory(ChannelServer.class.getClassLoader());
        if (configuration.getServerName() != null) {
            saslServerFactory = new ServerNameSaslServerFactory(saslServerFactory, configuration.getServerName());
        }
        if (configuration.getUriScheme() != null) {
            saslServerFactory = new ProtocolSaslServerFactory(saslServerFactory, configuration.getUriScheme());
        }

        OptionMap options = OptionMap.create(Options.SASL_POLICY_NOANONYMOUS, Boolean.TRUE, RemotingOptions.SASL_PROTOCOL, "remote");
//        OptionMap options = OptionMap.builder()
//                .set(Options.SASL_POLICY_NOANONYMOUS, Boolean.TRUE)
//                .set(RemotingOptions.SASL_PROTOCOL, "remote")
//                //.set(RemotingOptions.SERVER_NAME, "localhost")
//                .getMap();
        AcceptingChannel<? extends ConnectedStreamChannel> streamServer = networkServerProvider.createServer(configuration.getBindAddress(), options, domainBuilder.build(), saslServerFactory);
        return new ChannelServer(endpoint, registration, streamServer);
    }

    public Endpoint getEndpoint() {
        return endpoint;
    }

    public void addChannelOpenListener(final String channelName) throws ServiceRegistrationException {
        addChannelOpenListener(channelName, null);
    }

    public void addChannelOpenListener(final String channelName, final OpenListener openListener) throws ServiceRegistrationException {
        endpoint.registerService(channelName, new OpenListener() {
            public void channelOpened(final Channel channel) {
                if (openListener != null) {
                    openListener.channelOpened(channel);
                }
            }

            public void registrationTerminated() {
                if (openListener != null) {
                    openListener.registrationTerminated();
                }
            }
        }, OptionMap.EMPTY);

    }

    public void close() {
        IoUtils.safeClose(streamServer);
        IoUtils.safeClose(registration);
        IoUtils.safeClose(endpoint);
    }

    public static final class Configuration {
        private String endpointName;
        private OptionMap optionMap = OptionMap.EMPTY;
        private String uriScheme;
        private InetSocketAddress bindAddress;
        private Executor executor;
        private String serverName;

        public Configuration() {
        }

        void validate() {
            if (endpointName == null) {
                throw new IllegalArgumentException("Null endpoint name");
            }
            if (optionMap == null) {
                throw new IllegalArgumentException("Null option map");
            }
            if (uriScheme == null) {
                throw new IllegalArgumentException("Null protocol name");
            }
            if (bindAddress == null) {
                throw new IllegalArgumentException("Null bind address");
            }
        }

        public void setEndpointName(String endpointName) {
            this.endpointName = endpointName;
        }

        public String getEndpointName() {
            return endpointName;
        }

        public String getUriScheme() {
            return uriScheme;
        }

        public void setUriScheme(String uriScheme) {
            this.uriScheme = uriScheme;
        }

        public OptionMap getOptionMap() {
            return optionMap;
        }

        public void setOptionMap(OptionMap optionMap) {
            this.optionMap = optionMap;
        }

        public InetSocketAddress getBindAddress() {
            return bindAddress;
        }

        public void setBindAddress(final InetSocketAddress bindAddress) {
            this.bindAddress = bindAddress;
        }

        public Executor getExecutor() {
            return executor;
        }

        public void setExecutor(final Executor readExecutor) {
            this.executor = readExecutor;
        }

        public String getServerName() {
            return serverName;
        }

        public void setServerName(String serverName) {
            this.serverName = serverName;
        }
    }

}
