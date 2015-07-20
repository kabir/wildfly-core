/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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
package delete.me;

import java.security.Security;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.wildfly.security.WildFlyElytronProvider;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.security.auth.client.MatchRule;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Sequence;

/**
 * @author Kabir Khan
 */
public class __Throwaway {

    public static void main(String[] args) throws Exception {
        Security.addProvider(new WildFlyElytronProvider());

        ModelNode op = new ModelNode();
        op.get("operation").set("read-resource");
        op.get("address").setEmptyList();

        //digestMd5(op);
        anonymous(op);
    }

    private static void anonymous(ModelNode op) throws Exception {
        AuthenticationContext authenticationContext =
                AuthenticationContext.empty().with(MatchRule.ALL,
                        AuthenticationConfiguration.EMPTY.useAnonymous());
        OptionMap options = OptionMap.create(Options.SASL_MECHANISMS, Sequence.of("ANONYMOUS"), Options.SASL_POLICY_NOANONYMOUS, Boolean.FALSE);
        ModelControllerClient client = ModelControllerClient.Factory.create("localhost", 9999, authenticationContext, null, 600000);

        ModelNode result = client.execute(op);
        System.out.println(result);
        Assert.assertEquals("success", result.get("outcome").asString());

    }

    private static void digestMd5(ModelNode op) throws Exception {
        AuthenticationContext authenticationContext =
                AuthenticationContext.empty().with(MatchRule.ALL,
                        AuthenticationConfiguration.EMPTY.useName("kabir").usePassword("kabir").allowSaslMechanisms("DIGEST-MD5"));
        OptionMap options = OptionMap.create(Options.SASL_MECHANISMS, Sequence.of("DIGEST-MD5"), Options.SASL_POLICY_NOANONYMOUS, Boolean.TRUE);
        ModelControllerClient client = ModelControllerClient.Factory.create("localhost", 9999, authenticationContext, null, 600000);

        ModelNode result = client.execute(op);
        System.out.println(result);
        Assert.assertEquals("success", result.get("outcome").asString());
    }
}
