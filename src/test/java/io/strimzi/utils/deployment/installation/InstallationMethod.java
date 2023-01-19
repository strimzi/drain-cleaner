/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.utils.deployment.installation;

public class InstallationMethod {
    private String namespaceName;
    public InstallationMethod(String namespaceName) {
        this.namespaceName = namespaceName;
    }

    /**
     * Deploy Drain Cleaner
     */
    public void deploy() {

    }

    /**
     * Delete Drain Cleaner
     */
    public void delete() {

    }

    public String getNamespaceName() {
        return this.namespaceName;
    }
}
