/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionRequest;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionReview;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionReviewBuilder;
import io.fabric8.kubernetes.api.model.policy.v1.Eviction;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Dependent
@Path("/drainer")
public class ValidatingWebhook {
    private static final Logger LOG = LoggerFactory.getLogger(ValidatingWebhook.class);

    @Inject
    KubernetesClient client;

    @Inject
    Pattern matchingPattern;

    // Default constructor => used in production
    public ValidatingWebhook() {
    }

    // Parametrized constructor => used in tests
    public ValidatingWebhook(KubernetesClient client, Pattern matchingPattern) {
        this.client = client;
        this.matchingPattern = matchingPattern;
    }

    private ObjectMeta extractEvictionMetadata(AdmissionRequest request)    {
        if (request.getObject() instanceof Eviction) {
            LOG.debug("Received Eviction request of version v1");
            Eviction eviction = (Eviction) request.getObject();
            return eviction.getMetadata();
        } else if (request.getObject() instanceof io.fabric8.kubernetes.api.model.policy.v1beta1.Eviction) {
            LOG.debug("Received Eviction request of version v1beta1");
            io.fabric8.kubernetes.api.model.policy.v1beta1.Eviction eviction = (io.fabric8.kubernetes.api.model.policy.v1beta1.Eviction) request.getObject();
            return eviction.getMetadata();
        } else {
            return null;
        }
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public AdmissionReview webhook(AdmissionReview review) {
        LOG.debug("Received AdmissionReview request: {}", review);

        AdmissionRequest request = review.getRequest();
        ObjectMeta evictionMetadata = extractEvictionMetadata(request);

        if (evictionMetadata != null)   {
            if (matchingPattern.matcher(evictionMetadata.getName()).matches()) {
                String name = evictionMetadata.getName();
                String namespace = evictionMetadata.getNamespace();

                if (namespace == null) {
                    // Some applications (see https://github.com/strimzi/drain-cleaner/issues/34) might send the eviction
                    // request without the namespace. In such case, we use the namespace form the AdmissionRequest.
                    LOG.warn("There is no namespace in the Eviction request - trying to use namespace of the Admission request");
                    namespace = request.getNamespace();
                }

                if (name == null || namespace == null) {
                    LOG.warn("Failed to decode pod name or namespace from the eviction webhook (pod: {}, namespace: {})", name, namespace);
                } else {
                    LOG.info("Received eviction webhook for Pod {} in namespace {}", name, namespace);
                    annotatePodForRestart(name, namespace, request.getDryRun());
                }
            } else {
                LOG.info("Received eviction event which does not match any relevant pods.");
            }
        } else {
            LOG.warn("Weird, this does not seem to be an Eviction webhook.");
        }

        return new AdmissionReviewBuilder()
                .withNewResponse()
                    .withUid(request.getUid())
                    .withAllowed(true)
                .endResponse()
                .build();
    }

    void annotatePodForRestart(String name, String namespace, boolean dryRun)    {
        Pod pod = client.pods().inNamespace(namespace).withName(name).get();

        if (pod != null) {
            if (pod.getMetadata() != null
                    && pod.getMetadata().getLabels() != null
                    && "Kafka".equals(pod.getMetadata().getLabels().get("strimzi.io/kind"))) {
                if (pod.getMetadata().getAnnotations() == null) {
                    pod.getMetadata().setAnnotations(Map.of("strimzi.io/manual-rolling-update", "true"));
                    LOG.info("Pod {} in namespace {} should be annotated for restart", name, namespace);
                    patchPod(name, namespace, pod, dryRun);
                } else if (!"true".equals(pod.getMetadata().getAnnotations().get("strimzi.io/manual-rolling-update"))) {
                    Map<String, String> newAnnos = new HashMap<>(pod.getMetadata().getAnnotations());
                    newAnnos.put("strimzi.io/manual-rolling-update", "true");
                    pod.getMetadata().setAnnotations(newAnnos);
                    LOG.info("Pod {} in namespace {} should be annotated for restart", name, namespace);
                    patchPod(name, namespace, pod, dryRun);
                } else {
                    LOG.info("Pod {} in namespace {} is already annotated for restart", name, namespace);
                }

            } else {
                LOG.debug("Pod {} in namespace {} is not a Strimzi pod", name, namespace);
            }
        } else {
            LOG.warn("Pod {} in namespace {} was not found so cannot be annotated", name, namespace);
        }
    }

    void patchPod(String name, String namespace, Pod pod, boolean dryRun)   {
        if (!dryRun) {
            client.pods().inNamespace(namespace).withName(name).patch(pod);
            LOG.info("Pod {} in namespace {} was patched", name, namespace);
        } else {
            LOG.info("Pod {} in namespace {} was not patched because webhook is in dry-run mode.", name, namespace);
        }
    }
}