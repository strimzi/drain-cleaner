/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.utils.deployment.installation;

import io.strimzi.utils.Constants;
import io.strimzi.utils.Environment;
import io.strimzi.utils.StUtils;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static io.strimzi.utils.k8s.KubeClusterResource.helmClusterClient;

public class HelmInstallation extends InstallationMethod {

    public static final String HELM_CHART = Constants.USER_PATH + "/../packaging/helm-charts/helm3/strimzi-drain-cleaner/";
    public static final String HELM_RELEASE_NAME = "drain-cleaner-systemtests";

    public HelmInstallation(String namespaceName) {
        super(namespaceName);
    }

    @Override
    public void deploy() {
        Map<String, Object> values = new HashMap<>();
        // image registry config
        values.put("defaultImageRegistry", Environment.CLEANER_REGISTRY);

        // image repository config
        values.put("defaultImageRepository", Environment.CLEANER_ORG);

        // image tags config
        values.put("defaultImageTag", Environment.CLEANER_TAG);


        Path pathToChart = new File(HELM_CHART).toPath();
        helmClusterClient().install(pathToChart, HELM_RELEASE_NAME, values);
        StUtils.waitForDeploymentReady(this.getNamespaceName(), Constants.DEPLOYMENT_NAME);
    }

    @Override
    public void delete() {
        helmClusterClient().delete(this.getNamespaceName(), HELM_RELEASE_NAME);
        StUtils.waitForDeploymentDeletion(this.getNamespaceName(), Constants.DEPLOYMENT_NAME);
    }
}
