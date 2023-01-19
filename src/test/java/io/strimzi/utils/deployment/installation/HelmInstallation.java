/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.utils.deployment.installation;

import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.strimzi.utils.Constants;
import io.strimzi.utils.Environment;
import io.strimzi.utils.SecretUtils;
import io.strimzi.utils.StUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static io.strimzi.utils.k8s.KubeClusterResource.helmClusterClient;
import static io.strimzi.utils.k8s.KubeClusterResource.kubeClient;

public class HelmInstallation extends InstallationMethod {

    public static final String HELM_CHART = Constants.USER_PATH + "/packaging/helm-charts/helm3/strimzi-drain-cleaner/";
    public static final String HELM_RELEASE_NAME = "drain-cleaner-systemtests";
    private static final Logger LOGGER = LogManager.getLogger(HelmInstallation.class);

    public HelmInstallation(String namespaceName) {
        super(namespaceName);
    }

    @Override
    public void deploy() {
        // create DC namespace
        kubeClient().createNamespace(Constants.NAMESPACE);

        // create DC secret with our own certificate
        LOGGER.info("Creating secret in {} namespace", Constants.NAMESPACE);
        SecretBuilder secretBuilder = SecretUtils.createDrainCleanerSecret();

        Map<String, Object> values = new HashMap<>();

        if (Environment.CLEANER_REGISTRY != null) {
            // image registry config
            values.put("defaultImageRegistry", Environment.CLEANER_REGISTRY);
        }

        if (Environment.CLEANER_ORG != null) {
            // image repository config
            values.put("defaultImageRepository", Environment.CLEANER_ORG);
        }

        if (Environment.CLEANER_TAG != null) {
            // image tags config
            values.put("defaultImageTag", Environment.CLEANER_TAG);
        }

        // don't deploy certificate and issuer
        values.put("certManager.create", "false");

        // pass caBundle to installation
        values.put("secret.ca_bundle", secretBuilder.getData().get("tls.crt"));

        // don't deploy namespace
        values.put("namespace.create", "false");

        Path pathToChart = new File(HELM_CHART).toPath();
        LOGGER.info("Installing DrainCleaner via Helm");
        helmClusterClient().install(pathToChart, HELM_RELEASE_NAME, values);
        StUtils.waitForDeploymentReady(this.getNamespaceName(), Constants.DEPLOYMENT_NAME);
    }

    @Override
    public void delete() {
        LOGGER.info("Deleting DrainCleaner via Helm");
        helmClusterClient().delete(HELM_RELEASE_NAME);
        StUtils.waitForDeploymentDeletion(this.getNamespaceName(), Constants.DEPLOYMENT_NAME);
    }
}
