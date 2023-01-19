/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.utils;

import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.strimzi.utils.security.CertAndKeyFiles;
import io.strimzi.utils.security.SystemTestCertAndKey;
import io.strimzi.utils.security.SystemTestCertManager;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.x509.GeneralName;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static io.strimzi.utils.k8s.KubeClusterResource.kubeClient;

public class SecretUtils {
    public static SecretBuilder createDrainCleanerSecret() {
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

        SecretBuilder secretBuilder = retrieveSecretBuilderFromFile(certsPaths,
                StUtils.DRAIN_CLEANER_DEPLOYMENT_NAME, StUtils.DRAIN_CLEANER_NAMESPACE,
                Collections.singletonMap("app", StUtils.DRAIN_CLEANER_DEPLOYMENT_NAME), "kubernetes.io/tls");

        // replace Secret with our own generated certificates
        kubeClient().getClient().secrets().inNamespace(StUtils.DRAIN_CLEANER_NAMESPACE).createOrReplace(secretBuilder.build());

        return secretBuilder;
    }

    public static SecretBuilder retrieveSecretBuilderFromFile(final Map<String, String> certFilesPath, final String name,
                                                              final String namespace, final Map<String, String> labels,
                                                              final String secretType) {
        byte[] encoded;
        final Map<String, String> data = new HashMap<>();

        try {
            for (final Map.Entry<String, String> entry : certFilesPath.entrySet()) {
                encoded = Files.readAllBytes(Paths.get(entry.getValue()));

                final Base64.Encoder encoder = Base64.getEncoder();
                data.put(entry.getKey(), encoder.encodeToString(encoded));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return new SecretBuilder()
            .withType(secretType)
            .withData(data)
            .withNewMetadata()
                .withName(name)
                .withNamespace(namespace)
                .addToLabels(labels)
            .endMetadata();
    }
}
