/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.utils.security;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.security.PrivateKey;

import static java.nio.charset.StandardCharsets.UTF_8;

public class SystemTestCertManager {

    public static SystemTestCertAndKey generateRootCaCertAndKey(final String rootCaDn, final ASN1Encodable[] sanDnsNames) {
        return SystemTestCertAndKeyBuilder.rootCaCertBuilder()
            .withIssuerDn(rootCaDn)
            .withSubjectDn(rootCaDn)
            .withSanDnsNames(sanDnsNames)
            .build();
    }

    public static CertAndKeyFiles exportToPemFiles(SystemTestCertAndKey... certs) {
        if (certs.length == 0) {
            throw new IllegalArgumentException("List of certificates should has at least one element");
        }
        try {
            File keyFile = exportPrivateKeyToPemFile(certs[0].getPrivateKey());
            File certFile = exportCertsToPemFile(certs);
            return new CertAndKeyFiles(certFile, keyFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static File exportPrivateKeyToPemFile(PrivateKey privateKey) throws IOException {
        File keyFile = File.createTempFile("key-", ".key");
        try (JcaPEMWriter pemWriter = new JcaPEMWriter(new FileWriter(keyFile, UTF_8))) {
            pemWriter.writeObject(privateKey);
            pemWriter.flush();
        }
        return keyFile;
    }

    private static File exportCertsToPemFile(SystemTestCertAndKey... certs) throws IOException {
        File certFile = File.createTempFile("crt-", ".crt");
        try (JcaPEMWriter pemWriter = new JcaPEMWriter(new FileWriter(certFile, UTF_8))) {
            for (SystemTestCertAndKey certAndKey : certs) {
                pemWriter.writeObject(certAndKey.getCertificate());
            }
            pemWriter.flush();
        }
        return certFile;
    }
}
