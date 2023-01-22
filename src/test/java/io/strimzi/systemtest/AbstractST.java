/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.systemtest;

import io.strimzi.utils.Constants;
import io.strimzi.utils.StUtils;
import io.strimzi.utils.deployment.SetupDrainCleaner;
import io.strimzi.utils.k8s.KubeClusterResource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

import java.util.Collections;

import static io.strimzi.utils.k8s.KubeClusterResource.kubeClient;

public class AbstractST {

    private static final Logger LOGGER = LogManager.getLogger(AbstractST.class);

    protected static KubeClusterResource cluster;

    protected static SetupDrainCleaner setupDrainCleaner;

    @BeforeEach
    void beforeEachTest(TestInfo testInfo) {
        LOGGER.info(String.join("", Collections.nCopies(76, "#")));
        LOGGER.info(String.format("%s.%s - STARTED", testInfo.getTestClass().get().getName(), testInfo.getTestMethod().get().getName()));
    }

    @BeforeAll
    static void setupClusterAndDrainCleaner() {
        cluster = KubeClusterResource.getInstance();

        // simple teardown before all tests
        if (kubeClient().getNamespace(Constants.NAMESPACE) != null) {
            StUtils.deleteNamespaceWithWait(Constants.NAMESPACE);
        }

        setupDrainCleaner = new SetupDrainCleaner();
        setupDrainCleaner.deployDrainCleaner();
    }

    @AfterAll
    static void teardown() {
        setupDrainCleaner.deleteDrainCleaner();
        StUtils.deleteNamespaceWithWait(Constants.NAMESPACE);
    }
}
