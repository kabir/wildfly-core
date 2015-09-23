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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.jboss.as.protocol.ProtocolChannelClient;
import org.jboss.as.protocol.mgmt.ManagementChannelReceiver;
import org.jboss.as.protocol.mgmt.ManagementMessageHandler;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.OpenListener;
import org.jboss.threads.JBossThreadFactory;
import org.jboss.threads.QueueExecutor;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.security.auth.client.MatchRule;
import org.wildfly.security.password.Password;
import org.wildfly.security.password.PasswordFactory;
import org.wildfly.security.password.spec.DigestPasswordAlgorithmSpec;
import org.wildfly.security.password.spec.EncryptablePasswordSpec;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @version $Revision: 1.1 $
 */
public class RemoteChannelPairSetup implements RemotingChannelPairSetup {

    static final String ENDPOINT_NAME = "endpoint";
    static final String URI_SCHEME = "test123";
    static final String TEST_CHANNEL = "Test-Channel";
    static final int PORT = 32123;
    static final int EXECUTOR_MAX_THREADS = 20;
    private static final long EXECUTOR_KEEP_ALIVE_TIME = 60000;

    static final String ALGORITHM = "digest-md5";
    static final String REALM = "localhost";
    static final String USER = "TestUser";
    static final String PASSWORD = "TestUserPassword";

    ChannelServer channelServer;

    protected ExecutorService executorService;
    protected Channel serverChannel;
    protected Channel clientChannel;
    protected Connection connection;

    final CountDownLatch clientConnectedLatch = new CountDownLatch(1);

    static Password createPassword() throws NoSuchAlgorithmException, InvalidKeySpecException {
        PasswordFactory factory = PasswordFactory.getInstance(ALGORITHM);
        DigestPasswordAlgorithmSpec dpas = new DigestPasswordAlgorithmSpec(USER, REALM);
        EncryptablePasswordSpec encryptableSpec = new EncryptablePasswordSpec(PASSWORD.toCharArray(), dpas);
        return factory.generatePassword(encryptableSpec);
    }

    public Channel getServerChannel() {
        return serverChannel;
    }

    public Channel getClientChannel() {
        return clientChannel;
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    public void setupRemoting(final ManagementMessageHandler handler) throws IOException, InvalidKeySpecException, NoSuchAlgorithmException {
        //executorService = new ThreadPoolExecutor(16, 16, 1L, TimeUnit.DAYS, new LinkedBlockingQueue<Runnable>());
        ThreadFactory threadFactory = new JBossThreadFactory(new ThreadGroup("Remoting"), Boolean.FALSE, null, "Remoting %f thread %t", null, null);
        executorService = new QueueExecutor(EXECUTOR_MAX_THREADS / 4 + 1, EXECUTOR_MAX_THREADS, EXECUTOR_KEEP_ALIVE_TIME, TimeUnit.MILLISECONDS, 500, threadFactory, true, null);

        ChannelServer.Configuration configuration = new ChannelServer.Configuration();
        configuration.setEndpointName(ENDPOINT_NAME);
        configuration.setUriScheme(URI_SCHEME);
        configuration.setBindAddress(new InetSocketAddress("127.0.0.1", PORT));
        configuration.setExecutor(executorService);
        channelServer = ChannelServer.create(configuration);

        final Channel.Receiver receiver = ManagementChannelReceiver.createDelegating(handler);

        channelServer.addChannelOpenListener(TEST_CHANNEL, new OpenListener() {

            @Override
            public void registrationTerminated() {
            }

            @Override
            public void channelOpened(Channel channel) {
                serverChannel = channel;
                serverChannel.receiveMessage(receiver);
                clientConnectedLatch.countDown();
            }
        });
    }

    public void startChannels() throws IOException, URISyntaxException, InvalidKeySpecException, NoSuchAlgorithmException {
        ProtocolChannelClient.Configuration configuration = new ProtocolChannelClient.Configuration();
        configuration.setEndpoint(channelServer.getEndpoint());
        configuration.setUri(new URI("" + URI_SCHEME + "://localhost:" + PORT + ""));
        configuration.setOptionMap(OptionMap.create(Options.SASL_POLICY_NOANONYMOUS, Boolean.TRUE));
        Password password = createPassword();
        configuration.setAuthenticationContext(AuthenticationContext.empty().with(MatchRule.ALL, AuthenticationConfiguration.EMPTY.useName(USER).usePassword(password).allowSaslMechanisms("DIGEST-MD5")));
        ProtocolChannelClient client = ProtocolChannelClient.create(configuration);
        connection = client.connectSync();
        clientChannel = connection.openChannel(TEST_CHANNEL, OptionMap.EMPTY).get();
        try {
            clientConnectedLatch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void stopChannels() {
        IoUtils.safeClose(clientChannel);
        IoUtils.safeClose(serverChannel);
    }

    public void shutdownRemoting() throws IOException, InterruptedException {
        channelServer.close();
        executorService.shutdown();
        executorService.awaitTermination(1L, TimeUnit.DAYS);
        executorService.shutdownNow();
    }

}
