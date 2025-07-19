/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.StatusBuilder;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionRequest;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionReview;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionReviewBuilder;
import io.fabric8.kubernetes.api.model.policy.v1.Eviction;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.Arrays;
import java.util.List;

@Path("/drainer")
public class ValidatingWebhook {
    private static final Logger LOG = LoggerFactory.getLogger(ValidatingWebhook.class);

    private static final Pattern ZOOKEEPER_PATTERN = Pattern.compile(".+-zookeeper");
    private static final Pattern KAFKA_PATTERN = Pattern.compile(".+-kafka");
    private static final String STRIMZI_LABEL_KEY = "strimzi.io/name";

    @ConfigProperty(name = "strimzi.drain.kafka")
    boolean drainKafka;

    @ConfigProperty(name = "strimzi.drain.zookeeper")
    boolean drainZooKeeper;

    @ConfigProperty(name = "strimzi.deny.eviction")
    boolean denyEviction;

    @ConfigProperty(name = "strimzi.drain.watch.namespaces", defaultValue = "")
    String watchNamespaces;

    @Inject
    KubernetesClient client;

    // Default constructor => used in production
    @SuppressWarnings("unused")
    public ValidatingWebhook() {
    }

    // Parametrized constructor => used in tests
    public ValidatingWebhook(KubernetesClient client, boolean drainKafka, boolean drainZooKeeper, boolean denyEviction) {
        this.client = client;
        this.drainZooKeeper = drainZooKeeper;
        this.drainKafka = drainKafka;
        this.denyEviction = denyEviction;
        this.watchNamespaces = "";
    }

    // Parametrized constructor for tests with namespace filtering
    public ValidatingWebhook(KubernetesClient client, boolean drainKafka, boolean drainZooKeeper, boolean denyEviction, String watchNamespaces) {
        this.client = client;
        this.drainZooKeeper = drainZooKeeper;
        this.drainKafka = drainKafka;
        this.denyEviction = denyEviction;
        this.watchNamespaces = watchNamespaces;
    }

    private EvictionRequest extractEviction(AdmissionRequest request)    {
        if (request.getObject() instanceof Eviction eviction) {
            LOG.debug("Received Eviction request of version v1");

            // Extract the UID from preconditions if set
            String uid = eviction.getDeleteOptions() != null && eviction.getDeleteOptions().getPreconditions() != null ? eviction.getDeleteOptions().getPreconditions().getUid() : null;

            return new EvictionRequest(eviction.getMetadata().getName(), eviction.getMetadata().getNamespace(), uid);
        } else if (request.getObject() instanceof io.fabric8.kubernetes.api.model.policy.v1beta1.Eviction eviction) {
            LOG.debug("Received Eviction request of version v1beta1");

            // Extract the UID from preconditions if set
            String uid = eviction.getDeleteOptions() != null && eviction.getDeleteOptions().getPreconditions() != null ? eviction.getDeleteOptions().getPreconditions().getUid() : null;

            return new EvictionRequest(eviction.getMetadata().getName(), eviction.getMetadata().getNamespace(), uid);
        } else {
            return null;
        }
    }

    private boolean matchingLabel(Map<String, String> labels) {
        if (labels != null && labels.get(STRIMZI_LABEL_KEY) != null
                && "Kafka".equals(labels.get("strimzi.io/kind"))) {
            return drainKafka && KAFKA_PATTERN.matcher(labels.get(STRIMZI_LABEL_KEY)).matches()
                        || drainZooKeeper && ZOOKEEPER_PATTERN.matcher(labels.get(STRIMZI_LABEL_KEY)).matches();
        } else {
            return false;
        }
    }

    private boolean matchingUuid(String evictionUuid, String podUuid) {
        if (evictionUuid == null || evictionUuid.equals(podUuid))   {
            return true;
        } else {
            LOG.warn("The UUID {} from the eviction request does not match the UUID of the current pod {}. The request might be old and should be ignored.", evictionUuid, podUuid);
            return false;
        }
    }

    private boolean isNamespaceWatched(String namespace) {
        if (watchNamespaces == null || watchNamespaces.trim().isEmpty()) {
            // If no specific namespaces are configured, watch all namespaces
            return true;
        }

        List<String> watchedNamespaceList = Arrays.asList(watchNamespaces.split(","));
        return watchedNamespaceList.stream()
                .map(String::trim)
                .filter(ns -> !ns.isEmpty())
                .anyMatch(watchedNs -> watchedNs.equals(namespace));
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public AdmissionReview webhook(AdmissionReview review) {
        LOG.debug("Received AdmissionReview request: {}", review);

        AdmissionRequest request = review.getRequest();
        EvictionRequest eviction = extractEviction(request);

        if (eviction != null) {
            String name = eviction.name();
            String namespace = eviction.namespace();

            if (namespace == null) {
                // Some applications (see https://github.com/strimzi/drain-cleaner/issues/34) might send the eviction
                // request without the namespace. In such case, we use the namespace form the AdmissionRequest.
                LOG.warn("There is no namespace in the Eviction request - trying to use namespace of the Admission request");
                namespace = request.getNamespace();
            }

            if (name == null || namespace == null) {
                LOG.warn("Failed to decode pod name or namespace from the eviction webhook (pod: {}, namespace: {})", name, namespace);
            } else if (!isNamespaceWatched(namespace)) {
                LOG.debug("Ignoring eviction request for Pod {} in namespace {} - namespace not in watch list", name, namespace);
            } else {
                Pod pod = client.pods().inNamespace(namespace).withName(name).get();

                if (pod != null) {
                    if (matchingLabel(pod.getMetadata().getLabels())) {
                        LOG.info("Received eviction webhook for Pod {} in namespace {}", name, namespace);

                        if (matchingUuid(eviction.uid(), pod.getMetadata().getUid())) {
                            annotatePodForRestart(pod, request.getDryRun());

                            // The Pod should be rolled by the Strimzi Cluster Operator
                            //     => depending on the configuration, we deny or allow the eviction
                            if (denyEviction) {
                                LOG.info("Denying request for eviction of Pod {} in namespace {}", name, namespace);
                                return denyRequest(request);
                            } else {
                                LOG.info("Allowing request for eviction of Pod {} in namespace {}", name, namespace);
                                return allowRequest(request);
                            }
                        }
                    } else {
                        LOG.info("Received eviction event which does not match any relevant pods.");
                    }
                } else {
                    LOG.warn("No pod has been found with name {} in namespace {}", name, namespace);
                }
            }
        } else {
            LOG.warn("Weird, this does not seem to be an Eviction webhook");
        }

        // Does not seem like a request for us, but we will allow it if some other tool makes some sense of it
        return allowRequest(request);
    }

    private AdmissionReview allowRequest(AdmissionRequest request) {
        return new AdmissionReviewBuilder()
                .withNewResponse()
                    .withUid(request.getUid())
                    .withAllowed(true)
                .endResponse()
                .build();
    }

    private AdmissionReview denyRequest(AdmissionRequest request) {
        return new AdmissionReviewBuilder()
                .withNewResponse()
                    .withUid(request.getUid())
                    .withAllowed(false)
                    .withStatus(new StatusBuilder().withCode(500).withMessage("The pod will be rolled by the Strimzi Cluster Operator").build())
                .endResponse()
                .build();
    }

    void annotatePodForRestart(Pod pod, boolean dryRun) {
        String name = pod.getMetadata().getName();
        String namespace = pod.getMetadata().getNamespace();
        if (pod.getMetadata() != null) {
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
    }


    void patchPod(String name, String namespace, Pod pod, boolean dryRun)   {
        if (!dryRun) {
            client.pods().inNamespace(namespace).withName(name).patch(pod);
            LOG.info("Pod {} in namespace {} was patched", name, namespace);
        } else {
            LOG.info("Pod {} in namespace {} was not patched because webhook is in dry-run mode", name, namespace);
        }
    }

    record EvictionRequest(String name, String namespace, String uid) {
    }
}