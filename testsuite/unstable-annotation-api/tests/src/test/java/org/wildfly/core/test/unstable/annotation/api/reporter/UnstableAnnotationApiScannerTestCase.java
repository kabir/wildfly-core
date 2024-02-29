/*
 *  JBoss, Home of Professional Open Source.
 *  Copyright 2024 Red Hat, Inc., and individual contributors
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

package org.wildfly.core.test.unstable.annotation.api.reporter;

import jakarta.inject.Inject;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.management.ManagementOperations;
import org.jboss.as.test.integration.management.util.ServerReload;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.test.unstable.annotation.api.reporter.classes.AnnotatedClassExtendsUsage;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.WildFlyRunner;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RunWith(WildFlyRunner.class)
public class UnstableAnnotationApiScannerTestCase {

    @Inject
    private ManagementClient managementClient;

    private static final String INDEX_MODULE_DIR =
            "system/layers/base/org/wildfly/_internal/unstable-annotation-api-index/main";

    private static final String CONTENT = "content";
    private static final String INDEX_INDEX_FILE = "index.txt";

    private static final String TEST_FEATURE_PACK_INDEX = "wildfly-core-testsuite-unstable-annotation-api-feature-pack.txt";
    @Test
    public void testIndexExists() throws Exception {
        String jbossHomeProp = System.getProperty("jboss.home");

        Path jbossHome = Paths.get(jbossHomeProp);
        Assert.assertTrue(Files.exists(jbossHome));

        Path modulesPath = jbossHome.resolve("modules");
        Assert.assertTrue(Files.exists(modulesPath));

        Path indexModulePath = modulesPath.resolve(INDEX_MODULE_DIR);
        Assert.assertTrue(Files.exists(indexModulePath));

        Path indexContentDir = indexModulePath.resolve(CONTENT);
        Assert.assertTrue(Files.exists(indexContentDir));

        Path mainIndexFile = indexContentDir.resolve(INDEX_INDEX_FILE);
        Assert.assertTrue(Files.exists(mainIndexFile));

        List<String> mainIndexFileList = Files.readAllLines(mainIndexFile)
                .stream()
                .filter(s -> !s.isEmpty())
                .filter(s -> !s.startsWith("#"))
                .collect(Collectors.toList());

        Assert.assertEquals(1, mainIndexFileList.size());
        Assert.assertTrue(mainIndexFileList.contains(TEST_FEATURE_PACK_INDEX));

        Set<String> indices = Files.list(indexContentDir)
                .filter(p -> !p.getFileName().toString().equals(INDEX_INDEX_FILE))
                .map(p -> p.getFileName().toString())
                .collect(Collectors.toSet());

        Assert.assertEquals(1, indices.size());
        Assert.assertTrue(indices.toString(), indices.contains(TEST_FEATURE_PACK_INDEX));
    }

    @Test
    public void testDeploymentWarning() {
        LogDiffer logDiffer = new LogDiffer();
        logDiffer.takeSnapshot();
        // Deploy a deployment with unstable annotations
        // Check that the log contains the expected warning
    }

    @Test
    public void testDeploymentError() throws Exception {
        LogDiffer logDiffer = new LogDiffer();
        logDiffer.takeSnapshot();


        ModelControllerClient mcc = managementClient.getControllerClient();
        JavaArchive deployment = createDeploymentWithUnstableAnnotations();
        Operation deploymentOp = createDeploymentOp(deployment);

        try {
            ManagementOperations.executeOperation(mcc, deploymentOp);
            List<String> newLogEntries = logDiffer.getNewLogEntries();
            Assert.assertTrue(!newLogEntries.isEmpty());
        } finally {
            ManagementOperations.executeOperation(mcc, Util.createRemoveOperation(PathAddress.pathAddress("deployment", deployment.getName())));
            ServerReload.executeReloadAndWaitForCompletion(mcc);
        }

    }

    private void deployDeployment(JavaArchive deployment) throws Exception{
        ModelControllerClient mcc = managementClient.getControllerClient();
        Operation deploymentOp = createDeploymentOp(deployment);
        try {
            ManagementOperations.executeOperation(mcc, deploymentOp);
        } catch (Exception e) {
            ManagementOperations.executeOperation(mcc, Util.createRemoveOperation(PathAddress.pathAddress("deployment", deployment.getName())));
            ServerReload.executeReloadAndWaitForCompletion(mcc);
        }
    }

    private JavaArchive createDeploymentWithUnstableAnnotations() {
        JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "deployment-with-unstable-annotations.jar")
                .addClass(AnnotatedClassExtendsUsage.class);

        return archive;
    }

    private Operation createDeploymentOp(JavaArchive deployment) {
        final List<InputStream> streams = new ArrayList<>();
        streams.add(deployment.as(ZipExporter.class).exportAsInputStream());
        final ModelNode addOperation = Util.createAddOperation(PathAddress.pathAddress("deployment", deployment.getName()));
        addOperation.get("enabled").set(true);
        addOperation.get("content").add().get("input-stream-index").set(0);
        return Operation.Factory.create(addOperation, streams, true);
    }

    private static class LogDiffer {
        Path logFile;

        private List<String> lastLogSnapshot = Collections.emptyList();


        public LogDiffer() {
            String jbossHomeProp = System.getProperty("jboss.home");
            Path jbossHome = Paths.get(jbossHomeProp);
            Assert.assertTrue(Files.exists(jbossHome));
            this.logFile = jbossHome.resolve("standalone/log/server.log");
            Assert.assertTrue(Files.exists(logFile));
        }

        public void takeSnapshot() {
            try {
                lastLogSnapshot = Files.readAllLines(logFile);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public List<String> getNewLogEntries() {
            try {
                List<String> currentLog = Files.readAllLines(logFile);
                return currentLog.stream()
                        .filter(s -> !lastLogSnapshot.contains(s))
                        .filter(s -> s.contains("WFLYCM"))
                        .collect(Collectors.toList());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

    }
}
