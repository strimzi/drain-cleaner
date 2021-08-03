####
# Based on the Quarkus distroless image
###
FROM quay.io/quarkus/quarkus-distroless-image:1.0

# the use of --chown flag is a workaround to this Azure pipelines issue:
# https://github.com/microsoft/azure-pipelines-tasks/issues/6364
COPY --chown=nonroot:root target/strimzi-drain-cleaner-*-runner /application

EXPOSE 8080
USER nonroot

CMD ["./application", "-Dquarkus.http.host=0.0.0.0"]
