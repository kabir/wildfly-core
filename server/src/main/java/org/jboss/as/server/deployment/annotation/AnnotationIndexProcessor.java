/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment.annotation;

import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.DeploymentUtils;
import org.jboss.as.server.deployment.annotation.ResourceRootIndexer.TempClassCounter;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.as.server.logging.ServerLogger;

/**
 * Deployment unit processor responsible for creating and attaching an annotation index for a resource root
 *
 * @author John E. Bailey
 * @author Stuart Douglas
 */
public class AnnotationIndexProcessor implements DeploymentUnitProcessor {

    /**
     * Process this deployment for annotations.  This will use an annotation indexer to create an index of all annotations
     * found in this deployment and attach it to the deployment unit context.
     *
     * @param phaseContext the deployment unit context
     * @throws DeploymentUnitProcessingException
     *
     */
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {

        long start = System.currentTimeMillis();
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        TempClassCounter tempCounter = new TempClassCounter();
        for (ResourceRoot resourceRoot : DeploymentUtils.allResourceRoots(deploymentUnit)) {
            tempCounter.attach(resourceRoot);
            try {
                ResourceRootIndexer.indexResourceRoot(resourceRoot);
            } finally {
                tempCounter.detach(resourceRoot);
            }
        }
        ServerLogger.DEPLOYMENT_LOGGER.infof("----> Jandex took %d to scan %d classes", tempCounter.getTimeMs(), tempCounter.getClasses());
    }

}
