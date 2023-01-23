/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.utils.deployment;

import io.strimzi.utils.Environment;
import io.strimzi.utils.deployment.installation.BundleInstallation;
import io.strimzi.utils.deployment.installation.HelmInstallation;
import io.strimzi.utils.deployment.installation.InstallationMethod;
import io.strimzi.utils.enums.InstallType;

public class SetupDrainCleaner {
    private InstallationMethod installationMethod;

    public SetupDrainCleaner() {
        this.installationMethod = getInstallationMethod();
    }

    public void deployDrainCleaner() {
        installationMethod.deploy();
    }

    public void deleteDrainCleaner() {
        installationMethod.delete();
    }

    private InstallationMethod getInstallationMethod() {
        return Environment.INSTALL_TYPE == InstallType.Helm ? new HelmInstallation() : new BundleInstallation();
    }
}
