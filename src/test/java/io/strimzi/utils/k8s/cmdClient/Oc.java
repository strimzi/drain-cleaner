/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.utils.k8s.cmdClient;

import io.strimzi.utils.k8s.cluster.OpenShift;

/**
 * A {@link KubeCmdClient} implementation wrapping {@code oc}.
 */
public class Oc extends BaseCmdKubeClient<Oc> {

    private static final String OC = "oc";

    public Oc() { }

    private Oc(String futureNamespace) {
        namespace = futureNamespace;
    }

    @Override
    public String defaultNamespace() {
        return OpenShift.DEFAULT_NAMESPACE;
    }

    @Override
    public Oc namespace(String namespace) {
        return new Oc(namespace);
    }

    @Override
    public String cmd() {
        return OC;
    }
}
