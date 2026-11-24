/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.strimzi.utils.security.CertAndKeyFiles;
import io.strimzi.utils.security.SystemTestCertManager;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.x509.GeneralName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.CoreMatchers.is;

@QuarkusIntegrationTest
@QuarkusTestResource(value = ListenerRoutingIT.ListenerTestResource.class, restrictToAnnotatedClass = true)
class ListenerRoutingIT {
    // A non-eviction request exercises the webhook without contacting Kubernetes.
    private static final String ADMISSION_REVIEW = """
            {"apiVersion":"admission.k8s.io/v1","kind":"AdmissionReview","request":{"uid":"listener-test"}}
            """;

    @TestHTTPResource(management = true)
    URI management;

    @TestHTTPResource(tls = true)
    URI application;

    @Test
    void healthProbesAreAvailableOnlyOnTheManagementListener() {
        for (String path : new String[] {"/q/health/live", "/q/health/ready"}) {
            given().when().get(management.resolve(path))
                    .then().statusCode(200).contentType(JSON).body("status", is("UP"));
            given().relaxedHTTPSValidation().when().get(application.resolve(path))
                    .then().statusCode(404);
        }
    }

    @Test
    void webhookIsAvailableOnlyOnTheApplicationListener() {
        given().contentType(JSON).body(ADMISSION_REVIEW)
                .when().post(management.resolve("/drainer"))
                .then().statusCode(404);
        given().relaxedHTTPSValidation().contentType(JSON).body(ADMISSION_REVIEW)
                .when().post(application.resolve("/drainer"))
                .then().statusCode(200).body("response.uid", is("listener-test"))
                .body("response.allowed", is(true));
    }

    public static class ListenerTestResource implements QuarkusTestResourceLifecycleManager {
        private CertAndKeyFiles tlsFiles;

        @Override
        public Map<String, String> start() {
            tlsFiles = SystemTestCertManager.exportToPemFiles(
                    SystemTestCertManager.generateRootCaCertAndKey("CN=localhost",
                            new ASN1Encodable[] {new GeneralName(GeneralName.dNSName, "localhost")}));
            // Only override bind addresses, ports and certificate paths, not the production routing configuration.
            return Map.of(
                    "quarkus.http.host", "localhost",
                    "quarkus.http.port", "0",
                    "quarkus.http.ssl-port", "0",
                    "quarkus.http.ssl.certificate.files", tlsFiles.getCertPath(),
                    "quarkus.http.ssl.certificate.key-files", tlsFiles.getKeyPath(),
                    "quarkus.management.host", "localhost",
                    "quarkus.management.port", "0");
        }

        @Override
        public void stop() {
            if (tlsFiles != null) {
                try {
                    Files.deleteIfExists(Path.of(tlsFiles.getCertPath()));
                    Files.deleteIfExists(Path.of(tlsFiles.getKeyPath()));
                } catch (IOException e) {
                    throw new UncheckedIOException("Could not delete temporary TLS files", e);
                }
            }
        }
    }
}
