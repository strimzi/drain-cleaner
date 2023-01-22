/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.utils.deployment.installation;

import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.admissionregistration.v1.ValidatingWebhookConfiguration;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.strimzi.utils.Constants;
import io.strimzi.utils.SecretUtils;
import io.strimzi.utils.StUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static io.strimzi.utils.k8s.KubeClusterResource.cmdKubeClient;
import static io.strimzi.utils.k8s.KubeClusterResource.kubeClient;

public class BundleInstallation extends InstallationMethod {
    private static final Logger LOGGER = LogManager.getLogger(BundleInstallation.class);
    private static Stack<String> createdFiles = new Stack<>();

    @Override
    public void deploy() {
        List<File> drainCleanerFiles = Arrays.stream(new File(Constants.INSTALL_PATH).listFiles()).sorted()
                .filter(File::isFile)
                .collect(Collectors.toList());

        final AtomicReference<SecretBuilder> customDrainCleanerSecretBuilder = new AtomicReference<>();

        drainCleanerFiles.forEach(file -> {
            if (!file.getName().contains("README")) {
                if (!file.getName().contains("Deployment")) {
                    LOGGER.info(String.format("Creating file: %s", file.getAbsolutePath()));

                    if (file.getName().endsWith("040-Secret.yaml")) {
                        SecretBuilder secretBuilder = SecretUtils.createDrainCleanerSecret();
                        customDrainCleanerSecretBuilder.set(secretBuilder);
                    } else if (file.getName().endsWith("070-ValidatingWebhookConfiguration.yaml")) {
                        ValidatingWebhookConfiguration validatingWebhookConfiguration = StUtils.configFromYaml(file, ValidatingWebhookConfiguration.class);
                        // we fetch public key from strimzi-drain-cleaner Secret and then patch ValidationWebhookConfiguration.
                        validatingWebhookConfiguration.getWebhooks().stream().findFirst().get().getClientConfig().setCaBundle(customDrainCleanerSecretBuilder.get().getData().get("tls.crt"));
                        kubeClient().getClient().admissionRegistration().v1().validatingWebhookConfigurations().createOrReplace(validatingWebhookConfiguration);
                    } else {
                        cmdKubeClient().namespace(Constants.NAMESPACE).apply(file);
                    }
                    createdFiles.add(file.getAbsolutePath());
                } else {
                    deployDrainCleaner(file);
                }
            }
        });
    }

    @Override
    public void delete() {
        while (!createdFiles.empty()) {
            String fileToBeDeleted = createdFiles.pop();
            LOGGER.info("Deleting file: {}", fileToBeDeleted);
            cmdKubeClient().namespace(Constants.NAMESPACE).delete(fileToBeDeleted);
        }
    }

    private void deployDrainCleaner(File deploymentFile) {
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
        StUtils.waitForDeploymentReady(Constants.NAMESPACE, Constants.DEPLOYMENT_NAME);
    }
}
