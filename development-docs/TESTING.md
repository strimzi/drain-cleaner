# Testing

## Unit Tests

Some unit tests are included and are run automatically during the build.
You can also run them it manually by evicting pods or by posting admission reviews.

### Evicting pods

* Install the Drain Cleaner
* Proxy to the Kubernetes API server
  ```
  kubectl proxy
  ```
* Use `curl` to trigger eviction _(change pod name and namespace as needed)_:
  ```
  curl -v -H 'Content-type: application/json' http://localhost:8001/api/v1/namespaces/myproject/pods/my-cluster-zookeeper-1/eviction -d @src/test/resources/example-eviction-request.json
  ```

### Posting admission review requests

* Run Drain Cleaner locally (`mvn compile quarkus:dev`)
* Use `curl` to post the Admission Review Request manually:
  ```
  curl -v -H 'Content-type: application/json' http://localhost:8080/drainer -d @src/test/resources/example-admission-review.json
  ```

## System Tests

If you want to ensure that everything works, you can run system tests using:

```
mvn verify -Psystemtest
```

Before you run the tests, you should be logged in to Kubernetes or OpenShift cluster.

You can also specify environment variables, that will be used to configure the container image used during the tests:
* `DOCKER_REGISTRY` defines the registry from where the image should be pulled.
  For example `docker.io`.
* `DOCKER_ORG` defines the organization from where the image should be pulled.
  For example `my-org`.
* `DOCKER_TAG` defines the tag which should be used.
