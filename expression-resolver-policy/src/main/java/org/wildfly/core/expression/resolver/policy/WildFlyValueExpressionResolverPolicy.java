/*
 *  JBoss, Home of Professional Open Source.
 *  Copyright 2021 Red Hat, Inc., and individual contributors
 *  as indicated by the @author tags.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.wildfly.core.expression.resolver.policy;

import java.util.Locale;

import org.jboss.dmr.ValueExpressionResolverPolicy;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class WildFlyValueExpressionResolverPolicy implements ValueExpressionResolverPolicy {
    @Override
    public String resolve(String name) {
        // First check for system property, then env variable
        String val = System.getProperty(name);

        if (val == null) {
            // See if an env var is defined
            String envVar = replaceNonAlphanumericByUnderscores(name);
            envVar = name.toUpperCase(Locale.ENGLISH);
            envVar = envVar.replace(".", "_");
            val = System.getenv(envVar);
        }

        if (val == null && name.startsWith("env."))
            val = System.getenv(name.substring(4));

        return val;

    }

    private String replaceNonAlphanumericByUnderscores(final String name) {
        int length = name.length();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            char c = name.charAt(i);
            if ('a' <= c && c <= 'z' ||
                    'A' <= c && c <= 'Z' ||
                    '0' <= c && c <= '9') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        return sb.toString();
    }

}
