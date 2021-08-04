####
# Based on the Quarkus distroless image
###
FROM quay.io/quarkus/quarkus-distroless-image:1.0

COPY target/strimzi-drain-cleaner-*-runner /application

EXPOSE 8080
USER 1001

CMD ["./application", "-Dquarkus.http.host=0.0.0.0"]
