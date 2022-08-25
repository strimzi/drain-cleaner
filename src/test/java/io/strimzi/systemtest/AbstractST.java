/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.systemtest;

import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.admissionregistration.v1.ValidatingWebhookConfiguration;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.strimzi.utils.StUtils;
import io.strimzi.utils.k8s.KubeClusterResource;

import io.strimzi.utils.security.CertAndKeyFiles;
import io.strimzi.utils.security.SystemTestCertAndKey;
import io.strimzi.utils.security.SystemTestCertManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.x509.GeneralName;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static io.strimzi.utils.k8s.KubeClusterResource.cmdKubeClient;
import static io.strimzi.utils.k8s.KubeClusterResource.kubeClient;

public class AbstractST {

    private static final String USER_PATH = System.getProperty("user.dir");
    private static final Logger LOGGER = LogManager.getLogger(AbstractST.class);

    public static final String NAMESPACE = "strimzi-drain-cleaner";
    public static final String DEPLOYMENT_NAME = "strimzi-drain-cleaner";
    private static final String INSTALL_PATH = USER_PATH + "/install/kubernetes/";

    protected static KubeClusterResource cluster;
    private static Stack<String> createdFiles = new Stack<>();

    static void applyInstallFiles() {
        List<File> drainCleanerFiles = Arrays.stream(new File(INSTALL_PATH).listFiles()).sorted()
            .filter(File::isFile)
            .collect(Collectors.toList());

        final AtomicReference<SecretBuilder> customDrainCleanerSecretBuilder = new AtomicReference<>();

        drainCleanerFiles.forEach(file -> {
            if (!file.getName().contains("README")) {
                if (!file.getName().contains("Deployment")) {
                    LOGGER.info(String.format("Creating file: %s", file.getAbsolutePath()));

                    if (file.getName().endsWith("040-Secret.yaml")) {
                         // we need to create our own certificates before applying install-files
                        final SystemTestCertAndKey drainCleanerKeyPair = SystemTestCertManager
                            .generateRootCaCertAndKey("C=CZ, L=Prague, O=Strimzi Drain Cleaner, CN=StrimziDrainCleanerCA",
                                // add hostnames (i.e., SANs) to the certificate
                                new ASN1Encodable[] {
                                    new GeneralName(GeneralName.dNSName, StUtils.DRAIN_CLEANER_DEPLOYMENT_NAME),
                                    new GeneralName(GeneralName.dNSName, StUtils.DRAIN_CLEANER_DEPLOYMENT_NAME + "." + StUtils.DRAIN_CLEANER_DEPLOYMENT_NAME),
                                    new GeneralName(GeneralName.dNSName, StUtils.DRAIN_CLEANER_DEPLOYMENT_NAME + "." + StUtils.DRAIN_CLEANER_DEPLOYMENT_NAME + ".svc"),
                                    new GeneralName(GeneralName.dNSName, StUtils.DRAIN_CLEANER_DEPLOYMENT_NAME + "." + StUtils.DRAIN_CLEANER_DEPLOYMENT_NAME + ".svc.cluster.local")
                                });
                        final CertAndKeyFiles drainCleanerKeyPairPemFormat = SystemTestCertManager.exportToPemFiles(drainCleanerKeyPair);
                        final Map<String, String> certsPaths = new HashMap<>();
                        certsPaths.put("tls.crt", drainCleanerKeyPairPemFormat.getCertPath());
                        certsPaths.put("tls.key", drainCleanerKeyPairPemFormat.getKeyPath());

                        customDrainCleanerSecretBuilder.set(StUtils.retrieveSecretBuilderFromFile(certsPaths,
                            StUtils.DRAIN_CLEANER_DEPLOYMENT_NAME, StUtils.DRAIN_CLEANER_NAMESPACE,
                            Collections.singletonMap("app", StUtils.DRAIN_CLEANER_DEPLOYMENT_NAME), "kubernetes.io/tls"));

                        // replace Secret with our own generated certificates
                        kubeClient().getClient().secrets().inNamespace(StUtils.DRAIN_CLEANER_NAMESPACE).createOrReplace(customDrainCleanerSecretBuilder.get().build());
                    } else if (file.getName().endsWith("070-ValidatingWebhookConfiguration.yaml")) {
                        ValidatingWebhookConfiguration validatingWebhookConfiguration = StUtils.configFromYaml(file, ValidatingWebhookConfiguration.class);
                        // we fetch public key from strimzi-drain-cleaner Secret and then patch ValidationWebhookConfiguration.
                        validatingWebhookConfiguration.getWebhooks().stream().findFirst().get().getClientConfig().setCaBundle(customDrainCleanerSecretBuilder.get().getData().get("tls.crt"));
                        kubeClient().getClient().admissionRegistration().v1().validatingWebhookConfigurations().createOrReplace(validatingWebhookConfiguration);
                    } else {
                        cmdKubeClient().namespace(NAMESPACE).apply(file);
                    }
                    createdFiles.add(file.getAbsolutePath());
                } else {
                    deployDrainCleaner(file);
                }
            }
        });
    }

    static void deleteInstallFiles() {
        while (!createdFiles.empty()) {
            String fileToBeDeleted = createdFiles.pop();
            LOGGER.info("Deleting file: {}", fileToBeDeleted);
            cmdKubeClient().namespace(NAMESPACE).delete(fileToBeDeleted);
        }
    }

    private static void deployDrainCleaner(File deploymentFile) {
        Deployment drainCleanerDep = StUtils.configFromYaml(deploymentFile, Deployment.class);
        String deploymentImage = drainCleanerDep.getSpec().getTemplate().getSpec().getContainers().get(0).getImage();

        drainCleanerDep = new DeploymentBuilder(drainCleanerDep)
            .editSpec()
                .editTemplate()
                    .editSpec()
                        .editContainer(0)
                            .withImage(StUtils.changeOrgAndTag(deploymentImage))
                        .endContainer()
                    .endSpec()
                .endTemplate()
            .endSpec()
            .build();

        createdFiles.add(deploymentFile.getAbsolutePath());
        kubeClient().createOrReplaceDeployment(drainCleanerDep);
        StUtils.waitForDeploymentReady(NAMESPACE, DEPLOYMENT_NAME);
    }

    @BeforeEach
    void beforeEachTest(TestInfo testInfo) {
        LOGGER.info(String.join("", Collections.nCopies(76, "#")));
        LOGGER.info(String.format("%s.%s - STARTED", testInfo.getTestClass().get().getName(), testInfo.getTestMethod().get().getName()));
    }

    @BeforeAll
    static void setupClusterAndDrainCleaner() {
        cluster = KubeClusterResource.getInstance();

        // simple teardown before all tests
        if (kubeClient().getNamespace(NAMESPACE) != null) {
            StUtils.deleteNamespaceWithWait(NAMESPACE);
        }

        applyInstallFiles();
    }

    @AfterAll
    static void teardown() {
        deleteInstallFiles();
        StUtils.deleteNamespaceWithWait(NAMESPACE);
    }
}
