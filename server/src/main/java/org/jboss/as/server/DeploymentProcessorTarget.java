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

package org.jboss.as.server;

import java.util.function.Supplier;

import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.Phase;

/**
 * A target for deployment processors to be added to.
 */
public interface DeploymentProcessorTarget {

    /**
     * Add a deployment processor.
     *
     * @param subsystemName The name of the subsystem registering this processor
     * @param phase the processor phase install into (must not be {@code null})
     * @param priority the priority within the selected phase
     * @param processor the processor to install
     */
    @Deprecated
    void addDeploymentProcessor(String subsystemName, Phase phase, int priority, DeploymentUnitProcessor processor);


    /**
     * Add a deployment processor.
     *
     * This is a legacy method that does not take a subsystem name. A subsystem name
     * of the empty string is assumed.
     *
     * @param phase the processor phase install into (must not be {@code null})
     * @param priority the priority within the selected phase
     * @param processor the processor to install
     */
    @Deprecated
    void addDeploymentProcessor(Phase phase, int priority, DeploymentUnitProcessor processor);

    /**
     * Add a deployment processor.
     *
     * @param subsystemName The name of the subsystem registering this processor
     * @param phase the processor phase install into (must not be {@code null})
     * @param priority the priority within the selected phase
     * @param processorSupplier supplies the processor to install
     */
    void addDeploymentProcessor(final String subsystemName, final Phase phase, final int priority, final Supplier<DeploymentUnitProcessor> processorSupplier);
}
