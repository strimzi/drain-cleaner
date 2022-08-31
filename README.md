[![Build Status](https://dev.azure.com/cncf/strimzi/_apis/build/status/drain-cleaner?branchName=main)](https://dev.azure.com/cncf/strimzi/_build/latest?definitionId=36&branchName=main)
[![GitHub release](https://img.shields.io/github/release/strimzi/drain-cleaner.svg)](https://github.com/strimzi/drain-cleaner/releases/latest)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0)
[![Twitter Follow](https://img.shields.io/twitter/follow/strimziio.svg?style=social&label=Follow&style=for-the-badge)](https://twitter.com/strimziio)

# Strimzi Drain Cleaner

Strimzi Drain Cleaner is a utility which helps with moving the [Apache Kafka®](https://kafka.apache.org) pods deployed by [Strimzi](https://strimzi.io/) from Kubernetes nodes which are being drained.
It is useful if you want the Strimzi operator to move the pods instead of Kubernetes itself.
The advantage of this approach is that the Strimzi operator makes sure that no partition replicas become under-replicated during the node draining.
To use it:

* Configure your Kafka topics to have replication factor higher than 1 and make sure the `min.insync.replicas` is always set to a number lower than the replication factor.
  Availability of topics with replication factor `1` or with `min.insync.replicas` set to the same value as the replication factor will be always affected when the brokers are restarted.
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
To install the Drain Cleaner on OpenShift, use the `./install/openshift` directory:

```
kubectl apply -f ./install/openshift
```

### On Kubernetes with CertManager

On Kubernetes, when you use Cert Manager, you can have the certificates needed for the web-hook generated automatically and injected into the pod / web-hook configuration.
To install the Drain Cleaner on Kubernetes with installed CertManager, use the `./install/certmanager` directory:

```
kubectl apply -f ./install/certmanager
```

### On Kubernetes without CertManager

On Kubernetes, when you do not use Cert Manager, the certificates needed for the web-hook need to be generated manually.
Follow the instructions in the `./install/kubernetes` directory to generate and install the certificates.

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

## Getting help

If you encounter any issues while using Strimzi, you can get help using:

- [#strimzi channel on CNCF Slack](https://slack.cncf.io/)
- [Strimzi Users mailing list](https://lists.cncf.io/g/cncf-strimzi-users/topics)
- [GitHub Discussions](https://github.com/strimzi/drain-cleaner/discussions)

## Contributing

You can contribute by raising any issues you find and/or fixing issues by opening Pull Requests.
All bugs, tasks or enhancements are tracked as [GitHub issues](https://github.com/strimzi/drain-cleaner/issues).

The [development documentation](./development-docs) describe how to build, test and release Strimzi Drain Cleaner.

## License

Strimzi is licensed under the [Apache License](./LICENSE), Version 2.0

