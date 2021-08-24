package io.strimzi.test.systemtest;

import io.strimzi.utils.StUtils;
import io.strimzi.utils.k8s.KubeClusterResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;
import java.util.stream.Collectors;

import static io.strimzi.utils.k8s.KubeClusterResource.cmdKubeClient;
import static io.strimzi.utils.k8s.KubeClusterResource.kubeClient;

public class AbstractST {

    private static final String USER_PATH = System.getProperty("user.dir");
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractST.class);

    public static final String NAMESPACE = "strimzi-drain-cleaner";
    public static final String DEPLOYMENT_NAME = "strimzi-drain-cleaner";

    protected static KubeClusterResource cluster;
    private static Stack<String> createdFiles = new Stack<>();

    static void applyInstallFiles() {
        String installDirPath = cluster.isNotKubernetes() ? USER_PATH + "/install/openshift/" : USER_PATH + "/install/kubernetes/";

        List<File> drainCleanerFiles = Arrays.stream(new File(installDirPath).listFiles()).sorted()
            .filter(File::isFile)
            .collect(Collectors.toList());

        drainCleanerFiles.forEach(file -> {
            LOGGER.info("Creating file: {}", file.getAbsolutePath());
            cmdKubeClient().namespace(NAMESPACE).apply(file);
        });
    }

    static void deleteInstallFiles() {
        while (!createdFiles.empty()) {
            String fileToBeDeleted = createdFiles.pop();
            LOGGER.info("Deleting file: {}", fileToBeDeleted);
            cmdKubeClient().namespace(NAMESPACE).delete(fileToBeDeleted);
        }
    }

    @BeforeAll
    static void setupClusterAndDrainCleaner() {
        cluster = KubeClusterResource.getInstance();

        // simple teardown before all tests
        if (kubeClient().getNamespace(NAMESPACE) != null) {
            StUtils.deleteNamespaceWithWait(NAMESPACE);
        }

        applyInstallFiles();
        StUtils.waitForDeploymentReady(NAMESPACE, DEPLOYMENT_NAME);
    }

    @AfterAll
    static void teardown() {
        deleteInstallFiles();
        StUtils.deleteNamespaceWithWait(NAMESPACE);
    }
}
