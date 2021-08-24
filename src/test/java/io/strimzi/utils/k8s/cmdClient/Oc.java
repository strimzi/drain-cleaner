/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.utils.k8s.cmdClient;

import io.strimzi.utils.executor.Exec;
import io.strimzi.utils.k8s.cluster.OpenShift;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * A {@link io.strimzi.utils.k8s.cmdClient.KubeCmdClient} implementation wrapping {@code oc}.
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
    public String namespace() {
        return namespace;
    }

    @Override
    public String cmd() {
        return OC;
    }
}
