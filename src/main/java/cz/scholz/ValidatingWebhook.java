package cz.scholz;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionRequest;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionReview;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionReviewBuilder;
import io.fabric8.kubernetes.api.model.policy.Eviction;
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

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public AdmissionReview webhook(AdmissionReview review) {
        LOG.debug("Received AdmissionReview request: {}", review);

        AdmissionRequest request = review.getRequest();

        if (request.getObject() instanceof Eviction)    {
            Eviction eviction = (Eviction) request.getObject();

            if (eviction.getMetadata() != null
                    && matchingPattern.matcher(eviction.getMetadata().getName()).matches()) {
                String name = eviction.getMetadata().getName();
                String namespace = eviction.getMetadata().getNamespace();

                LOG.info("Received eviction webhook for Pod {} in namespace {}", name, namespace);

                if (request.getDryRun())    {
                    LOG.info("Running in dry-run mode. Pod {} in namespace {} will not be annotated for restart", name, namespace);
                } else {
                    LOG.info("Pod {} in namespace {} will be annotated for restart", name, namespace);
                    annotatePodForRestart(name, namespace);
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

    void annotatePodForRestart(String name, String namespace)    {
        Pod pod = client.pods().inNamespace(namespace).withName(name).get();

        if (pod != null) {
            if (pod.getMetadata() != null
                    && pod.getMetadata().getLabels() != null
                    && "Kafka".equals(pod.getMetadata().getLabels().get("strimzi.io/kind"))) {
                if (pod.getMetadata().getAnnotations() == null
                        || !"true".equals(pod.getMetadata().getAnnotations().get("strimzi.io/manual-rolling-update"))) {
                    pod.getMetadata().getAnnotations().put("strimzi.io/manual-rolling-update", "true");
                    client.pods().inNamespace(namespace).withName(name).patch(pod);

                    LOG.info("Pod {} in namespace {} found and annotated for restart", name, namespace);
                } else {
                    LOG.info("Pod {} in namespace {} is already annotated for restart", name, namespace);
                }


            } else {
                LOG.debug("Pod {} in namespace {} is not Strimzi pod", name, namespace);
            }
        } else {
            LOG.warn("Pod {} in namespace {} was not found and cannot be annotated", name, namespace);
        }
    }
}