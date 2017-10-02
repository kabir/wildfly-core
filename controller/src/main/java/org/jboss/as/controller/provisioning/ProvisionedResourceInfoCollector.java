/*
 * JBoss, Home of Professional Open Source
 * Copyright 2017, JBoss Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.controller.provisioning;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ProcessType;
import org.jboss.dmr.ModelNode;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * Class used to gather a list of the resources added in order when booting the server.
 *
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ProvisionedResourceInfoCollector {
    public static final String PROPERTY ="jboss.as.provision";

    private final boolean provisioning;
    private volatile boolean locked;
    private final List<PathAddress> addresses = Collections.synchronizedList(new ArrayList<>());

    private ProvisionedResourceInfoCollector(boolean provisioning) {
        this.provisioning = provisioning;
        if (!provisioning) {
            locked = true;
        }
    }

    public static ProvisionedResourceInfoCollector create(ProcessType processType) {
        boolean provisioning = (processType == ProcessType.STANDALONE_SERVER || processType == ProcessType.EMBEDDED_SERVER) &&
                Boolean.valueOf(WildFlySecurityManager.getPropertyPrivileged(PROPERTY, "false"));
        return new ProvisionedResourceInfoCollector(provisioning);
    }

    /**
     * Once called we will no longer gather information
     */
    public void lock() {
        locked = true;
    }

    /**
     * Get whether we are provisioning or not
     *
     * @return {@code true} if we are provisioning, {@code false} if not
     */
    public boolean isProvisioning() {
        return provisioning;
    }

    public List<PathAddress> getAddresses() {
        return addresses;
    }

    public void addOperation(OperationContext context, ModelNode operation) {
        if (locked) {
            return;
        }
        if (context.getCurrentStage() == OperationContext.Stage.MODEL) {
            if (operation.get(OP).asString().equals(ADD)) {
                addresses.add(context.getCurrentAddress());
            }
        }
    }
}
