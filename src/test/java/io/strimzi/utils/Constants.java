/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.utils;

public interface Constants {
    String USER_PATH = System.getProperty("user.dir");
    String INSTALL_PATH = USER_PATH + "/packaging/install/kubernetes/";
    String DEPLOYMENT_NAME = "strimzi-drain-cleaner";
    String NAMESPACE = "strimzi-drain-cleaner";
    String DEPLOYMENT = "deployment";
}
