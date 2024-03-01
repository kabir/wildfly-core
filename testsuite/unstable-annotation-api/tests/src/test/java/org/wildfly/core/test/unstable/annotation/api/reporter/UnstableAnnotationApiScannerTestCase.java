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
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.ServerSetupTask;
import org.wildfly.core.testrunner.WildFlyRunner;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYSTEM_PROPERTY;

@RunWith(WildFlyRunner.class)
@ServerSetup({UnstableAnnotationApiScannerTestCase.SystemPropertyServerSetupTask.class})
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
    public void testDeploymentError() {
        LogDiffer logDiffer = new LogDiffer();
        logDiffer.takeSnapshot();
        // Deploy a deployment with unstable annotations
        // Check that the log contains the expected warning
    }

    @Test
    public void testDeploymentWarning() throws Exception {
        LogDiffer logDiffer = new LogDiffer();
        logDiffer.takeSnapshot();


        ModelControllerClient mcc = managementClient.getControllerClient();
        JavaArchive deployment = createDeploymentWithUnstableAnnotations();
        Operation deploymentOp = createDeploymentOp(deployment);

        try {
            ManagementOperations.executeOperation(mcc, deploymentOp);
            List<String> newLogEntries = logDiffer.getNewLogEntries();
            Assert.assertTrue(!newLogEntries.isEmpty());
            Assert.assertEquals(8, newLogEntries.size());
            checkExpectedNumberClasses(newLogEntries, 6);

            checkLogLine(newLogEntries.get(1), "WFLYCM0009", "deployment-with-unstable-annotations.jar");
            checkLogLine(newLogEntries.get(2), "WFLYCM0010",
                    "org.wildfly.core.test.unstable.annotation.api.reporter.classes.AnnotatedClassExtendsUsage",
                    "org.wildfly.core.test.unstable.annotation.classes.api.TestClassWithAnnotationForExtends");
            checkLogLine(newLogEntries.get(3), "WFLYCM0011",
                    "org.wildfly.core.test.unstable.annotation.api.reporter.classes.AnnotatedInterfaceImplementsUsage",
                    "org.wildfly.core.test.unstable.annotation.classes.api.TestInterfaceWithAnnotation");
            checkLogLine(newLogEntries.get(4), "WFLYCM0012",
                    "org.wildfly.core.test.unstable.annotation.api.reporter.classes.AnnotatedFieldUsage",
                    "org.wildfly.core.test.unstable.annotation.classes.api.TestClassWithAnnotatedField.annotatedField");
            checkLogLine(newLogEntries.get(5), "WFLYCM0013",
                    "org.wildfly.core.test.unstable.annotation.api.reporter.classes.AnnotatedMethodUsage",
                    "org.wildfly.core.test.unstable.annotation.classes.api.TestClassWithAnnotatedMethod.annotatedMethod()V");
            checkLogLine(newLogEntries.get(6), "WFLYCM0014",
                    "org.wildfly.core.test.unstable.annotation.api.reporter.classes.AnnotatedClassUsage",
                    "org.wildfly.core.test.unstable.annotation.classes.api.TestClassWithAnnotationForUsage");
            checkLogLine(newLogEntries.get(7), "WFLYCM0015",
                    "org.wildfly.core.test.unstable.annotation.api.reporter.classes.AnnotatedAnnotationUsage");


        } finally {
            ManagementOperations.executeOperation(mcc, Util.createRemoveOperation(PathAddress.pathAddress("deployment", deployment.getName())));
            //ServerReload.executeReloadAndWaitForCompletion(mcc);
        }

    }

    private void checkExpectedNumberClasses(List<String> newLogEntries, int numberClasses) {
        Assert.assertTrue(!newLogEntries.isEmpty());
        String last = newLogEntries.get(0);
        checkLogLine(last, "WFLYCM0016", String.valueOf(numberClasses));
    }

    private void checkLogLine(String logLine, String loggingId, String...values) {
        int index = logLine.indexOf(loggingId);
        Assert.assertTrue("'" + logLine + "' does not contain '" + loggingId + "'",index != -1);
        index += loggingId.length();
        Assert.assertTrue(index < logLine.length());
        String valuesPart = logLine.substring(index);
        Set<String> words = new HashSet<>(Arrays.asList(logLine.split(" ")));
        for (String value : values) {
            Assert.assertTrue("'" + logLine + "' does not contain '" + value + "'", words.contains(value));
        }
    }

    private JavaArchive createDeploymentWithUnstableAnnotations() {
        JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "deployment-with-unstable-annotations.jar")
                .addPackage(AnnotatedClassExtendsUsage.class.getPackage());

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


    public static class SystemPropertyServerSetupTask implements ServerSetupTask {
        @Override
        public void setup(ManagementClient managementClient) throws Exception {
            ModelNode op = Util.createAddOperation(PathAddress.pathAddress(SYSTEM_PROPERTY, "org.wildfly.test.unstable-annotation-api.extra-output"));
            op.get("value").set("true");
            ManagementOperations.executeOperation(managementClient.getControllerClient(), op);
            // Reload so the system property is picked up by the deployer in order to print extra information
            // about class count
            ServerReload.executeReloadAndWaitForCompletion(managementClient.getControllerClient());
        }

        @Override
        public void tearDown(ManagementClient managementClient) throws Exception {
            ModelNode op = Util.createAddOperation(PathAddress.pathAddress(SYSTEM_PROPERTY, "org.wildfly.test.unstable-annotation-api.extra-output"));
            op.get("value").set("true");
            managementClient.getControllerClient().execute(op);
        }
    }
}
