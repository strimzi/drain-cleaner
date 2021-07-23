[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0)
[![Twitter Follow](https://img.shields.io/twitter/follow/strimziio.svg?style=social&label=Follow&style=for-the-badge)](https://twitter.com/strimziio)

# Strimzi Drain Cleaner

Strimzi Drain Cleaner is an utility which helps with moving the Kafka pods deployed by [Strimzi](https://strimzi.io/) from Kubernetes nodes which are being drained.
It is useful if you want the Strimzi operator to move the pods instead of Kubernetes itself.
The advantage of this approach is that the Strimzi operator makes sure that no pods become under-replicated during the node draining.
To use it:

* Deploy Kafka using Strimzi and configure the `PodDisruptionBudgets` for Kafka and ZooKeeper to have `maxUnavailable` set to `0`.
This will block Kubernetes from moving the pods on their own.
  
```yaml
apiVersion: kafka.strimzi.io/v1beta2
kind: Kafka
metadata:
  name: my-cluster
spec:
  kafka:
    replicas: 3
    listeners:
      - name: plain
        port: 9092
        type: internal
        tls: false
      - name: tls
        port: 9093
        type: internal
        tls: true
    config:
      offsets.topic.replication.factor: 3
      transaction.state.log.replication.factor: 3
      transaction.state.log.min.isr: 2
    storage:
      type: jbod
      volumes:
      - id: 0
        type: persistent-claim
        size: 100Gi
        deleteClaim: false
    template:
      podDisruptionBudget:
        maxUnavailable: 0
  zookeeper:
    replicas: 3
    storage:
      type: persistent-claim
      size: 100Gi
      deleteClaim: false
    template:
      podDisruptionBudget:
        maxUnavailable: 0
  entityOperator:
    topicOperator: {}
    userOperator: {}
```

* Deploy the Strimzi Drain Cleaner
* Drain the node with some Kafka or ZooKeeper pods using the [`kubectl drain` command](https://kubernetes.io/docs/tasks/administer-cluster/safely-drain-node/)

## How does it work?

Strimzi Drain Cleaner uses Kubernetes Admission Control features and Validating Web-hooks to find out when something tries to evict the Kafka or ZooKeeper pods.
It annotates them with the `strimzi.io/manual-rolling-update` annotation which will tell Strimzi Cluster Operator that this pod needs to be restarted.
Strimzi will roll it in the next reconciliation using its algorithms which make sure the cluster is available.
**This is supported from Strimzi 0.21.0.**

## Deployment

If you want to use this only to Kafka and not to ZooKeeper, you can edit the Deployment and remove the `--zookeeper` option.

### On OpenShift

On OpenShift, you can have the certificates needed for the web-hook generated automatically and injected into the pod / web-hook configuration.
To install the Drain Cleaner on OpenShift, use the `./deploy/openshift` directory:

```
kubectl apply -f ./deploy/openshift
```

### On Kubernetes with CertManager

On Kubernetes, when you use Cert Manager, you can have the certificates needed for the web-hook generated automatically and injected into the pod / web-hook configuration.
To install the Drain Cleaner on Kubernetes with installed CertManager, use the `./deploy/certmanager` directory:

```
kubectl apply -f ./deploy/certmanager
```

### On Kubernetes without CertManager

On Kubernetes, when you do not use Cert Manager, the certificates needed for the web-hook need to be generated manually.
Follow the instructions in `./deploy/kubernetes` directory.

## See it in action

You can easily test how it works:
* Install Strimzi on your cluster
* Deploy Kafka cluster with Pod Disruption Budget configuration having `maxUnavailable` set to `0` as shown in the example above
* Install the Drain Cleaner
* Drain one of the Kubernetes nodes with one of the Kafka or ZooKeeper pods
    ```
    kubectl drain <worker-node> --delete-emptydir-data --ignore-daemonsets --timeout=6000s --force
    ```
* Watch how it works:
    * The `kubetl drain` command will wait for the Kafka / ZooKeeper to be drained
    * The Drain Cleaner log should show how it gets the eviction events
    * Strimzi Cluster Operator log should show how it rolls the pods which are being evicted
    
## Build 

This project uses [Quarkus, the Supersonic Subatomic Java Framework](https://quarkus.io/).

### Running the application in dev mode

You can run your application in dev mode that enables live coding using:
```shell script
mvn compile quarkus:dev
```

### Creating a native executable

You can create a native executable using: 
```shell script
mvn package -Pnative
```

Or you can run the native executable build for Linux in a container using: 
```shell script
mvn package -Pnative -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./target/strimzi-drain-cleaner-1.0.0-SNAPSHOT-runner`.

### Building a container image

You can build the container for example using the _distro-less_ base image (use your own repository ;-)):

```
docker build -f src/main/docker/Dockerfile.native-distroless -t quay.io/scholzj/strimzi-cistic-odpadu:latest .
docker push quay.io/scholzj/strimzi-cistic-odpadu:latest
```

## Test

Some unit tests are included.
You can also test it manually by evicting pods or by posting admission reviews.

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
