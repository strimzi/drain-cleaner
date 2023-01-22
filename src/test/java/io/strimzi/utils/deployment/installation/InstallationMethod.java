/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.utils.deployment.installation;

public abstract class InstallationMethod {
    /**
     * Deploy Drain Cleaner
     */
    public abstract void deploy();

    /**
     * Delete Drain Cleaner
     */
    public abstract void delete();
}
