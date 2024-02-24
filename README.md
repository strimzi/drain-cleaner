[![Build Status](https://dev.azure.com/cncf/strimzi/_apis/build/status/drain-cleaner?branchName=main)](https://dev.azure.com/cncf/strimzi/_build/latest?definitionId=36&branchName=main)
[![GitHub release](https://img.shields.io/github/release/strimzi/drain-cleaner.svg)](https://github.com/strimzi/drain-cleaner/releases/latest)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0)
[![Twitter Follow](https://img.shields.io/twitter/follow/strimziio?style=social)](https://twitter.com/strimziio)
[![Artifact Hub](https://img.shields.io/endpoint?url=https://artifacthub.io/badge/repository/strimzi-drain-cleaner)](https://artifacthub.io/packages/search?repo=strimzi-drain-cleaner)

# Strimzi Drain Cleaner

Strimzi Drain Cleaner is a utility which helps with moving the [Apache Kafka®](https://kafka.apache.org) pods deployed by [Strimzi](https://strimzi.io/) from Kubernetes nodes which are being drained.
It is useful if you want the Strimzi operator to move the pods instead of Kubernetes itself.
The advantage of this approach is that the Strimzi operator makes sure that no partition replicas become under-replicated during the node draining.
To use it:

* Configure your Kafka topics to have replication factor higher than 1 and make sure the `min.insync.replicas` is always set to a number lower than the replication factor.
  Availability of topics with replication factor `1` or with `min.insync.replicas` set to the same value as the replication factor will always be affected when the brokers are restarted.
* Deploy Kafka using Strimzi.
  
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
    # Uncomment when using the legacy mode 
    # template:
    #   podDisruptionBudget:
    #     maxUnavailable: 0
  zookeeper:
    replicas: 3
    storage:
      type: persistent-claim
      size: 100Gi
      deleteClaim: false
    # Uncomment when using the legacy mode 
    # template:
    #   podDisruptionBudget:
    #     maxUnavailable: 0
  entityOperator:
    topicOperator: {}
    userOperator: {}
```

* Deploy the Strimzi Drain Cleaner
* Drain the node with some Kafka or ZooKeeper pods using the [`kubectl drain` command](https://kubernetes.io/docs/tasks/administer-cluster/safely-drain-node/)

## How does it work?

Strimzi Drain Cleaner uses Kubernetes Admission Control features and Validating Web-hooks to find out when something tries to evict the Kafka or ZooKeeper pods.
When it receives the eviction request for one of the Strimzi managed Kafka or ZooKeeper pods, it annotates them with the `strimzi.io/manual-rolling-update` annotation which will tell Strimzi Cluster Operator that this pod needs to be restarted and denies the eviction request.
Denying the eviction request prevents Kubernetes from restarting the Pod on their own based only on the `PodDisruptionBudget` configuration and leaves it to the Strimzi Cluster operator.
Strimzi Cluster Operator will roll it in the next reconciliation using its algorithms which make sure the cluster is available while the Pod is restarted.
Strimzi Cluster Operator will always roll the pods one-by-one regardless of the Pod Disruption Policy settings.
**This is supported from Strimzi 0.21.0.**

### Legacy mode

Different Kubernetes distributions and tools might react differently to the eviction request being denied.
If the tools used by your Kubernetes cluster do not handle it well, you can switch the Drain Cleaner into a _legacy_ mode where the eviction requests will be allowed.
To do so, you have to:
* Edit the Drain Cleaner `Deployment` and set the `STRIMZI_DENY_EVICTION` environment variable to `false`.
* Configure the `PodDisruptionBudgets` for Kafka and ZooKeeper to have `maxUnavailable` set to `0`.
  You can configure this in the `Kafka` custom resource in `spec.kafka.template.podDisruptionBudget.maxUnavailable` and `spec.zookeeper.template.podDisruptionBudget.maxUnavailable`.

Once running in the _legacy_ mode, the Drain Cleaner will still annotate the pods with the `strimzi.io/manual-rolling-update` annotation.
But it will allow the eviction request.
The eviction request will be ignored by Kubernetes because of the PodDisruptionBudget having `maxUnavailable` set to `0` and the Pod will be rolled by the Strimzi Cluster Operator.

## Deployment

By default, the Drain Cleaner drains Kafka and ZooKeeper pods. 
If you want to use the Drain Cleaner with only one of them, you can edit the `Deployment` by setting the `STRIMZI_DRAIN_KAFKA` or `STRIMZI_DRAIN_ZOOKEEPER` environment variables to `false`.

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

### On Kubernetes using Helm Chart

On Kubernetes, you can also use Helm to install Strimzi Drain Cleaner using our Helm Chart.
The Helm Chart can be used to install it both with Cert Manager support and with your own certificates.

### Certificate renewals

By default, the Drain Cleaner deployment is watching the Kubernetes secret with TLS certificates for changes such as certificate renewals.
If it detects such change, it will restart itself to reload the TLS certificate.
The Drain Cleaner installation files enable this by default.
But you can disable this by setting the `STRIMZI_CERTIFICATE_WATCH_ENABLED` environment variable to `false`.

When enabled, can also use the following environment variables to configure the detailed behavior:

| Environment Variable                     | Description                                                                               | Default                 |
|------------------------------------------|-------------------------------------------------------------------------------------------|-------------------------|
| `STRIMZI_CERTIFICATE_WATCH_ENABLED`      | Enables or disables the certificate watch                                                 | false                   |
| `STRIMZI_CERTIFICATE_WATCH_NAMESPACE`    | The namespace where the Drain Cleaner is deployed and where the certificate secret exists | `strimzi-drain-cleaner` |
| `STRIMZI_CERTIFICATE_WATCH_POD_NAME`     | The Drain Cleaner Pod name                                                                |                         |
| `STRIMZI_CERTIFICATE_WATCH_SECRET_NAME`  | The name of the secret with TLS certificates                                              | `strimzi-drain-cleaner` |
| `STRIMZI_CERTIFICATE_WATCH_SECRET_KEYS`  | The list of fields inside the secret which contain the TLS certificates                   | `tls.crt,tls.key`       |

The best way to configure `STRIMZI_CERTIFICATE_WATCH_NAMESPACE` and `STRIMZI_CERTIFICATE_WATCH_POD_NAME` is using the [Kubernetes Downward API](https://kubernetes.io/docs/concepts/workloads/pods/downward-api/).

## See it in action

You can easily test how it works:
* Install Strimzi on your cluster
* Deploy Kafka cluster
* Install the Drain Cleaner
* Drain one of the Kubernetes nodes with one of the Kafka or ZooKeeper pods
    ```
    kubectl drain <worker-node> --delete-emptydir-data --ignore-daemonsets --timeout=6000s --force
    ```
* Watch how it works:
    * The `kubetl drain` command will cordon the worker node and trigger the eviction of the Kafka / ZooKeeper pods running on it.
      It will evict the Pods that can be evicted and eventually fail because Drain Cleaner denied the eviction of the Kafka and ZooKeeper pods
      ```
      kubectl drain <worker-node> --delete-emptydir-data --ignore-daemonsets --timeout=6000s
      <worker-node> cordoned
      evicting pod myproject/my-cluster-kafka-1
      evicting pod myproject/my-cluster-entity-operator-8499d956cb-r8b5t
      ...
      pod/my-cluster-entity-operator-8499d956cb-r8b5t evicted
      ...
      There are pending pods in node "<worker-node>" when an error occurred: error when evicting pods/"my-cluster-kafka-1" -n "myproject": admission webhook "strimzi-drain-cleaner.strimzi.io" denied the request: The pod will be rolled by the Strimzi Cluster Operator
      pod/my-cluster-controllers-1
      error: unable to drain node "<worker-node>" due to error:error when evicting pods/"my-cluster-kafka-1" -n "myproject": admission webhook "strimzi-drain-cleaner.strimzi.io" denied the request: The pod will be rolled by the Strimzi Cluster Operator, continuing command...
      There are pending nodes to be drained:
      <worker-node>
      error when evicting pods/"my-cluster-kafka-1" -n "myproject": admission webhook "strimzi-drain-cleaner.strimzi.io" denied the request: The pod will be rolled by the Strimzi Cluster Operator
      ```
    * The Drain Cleaner log should show how it gets the eviction events
    * Strimzi Cluster Operator log should show how it rolls the pods which are being evicted
    * Once the Pods are restarted, you can retry the `kubectl drain` command and it should succeed
      ```
      kubectl <worker-node> --delete-emptydir-data --ignore-daemonsets --timeout=6000s
      <worker-node> already cordoned
      Warning: ignoring DaemonSet-managed Pods: ...
      <worker-node> drained
      ```

## Getting help

If you encounter any issues while using Strimzi, you can get help using:

- [#strimzi channel on CNCF Slack](https://slack.cncf.io/)
- [Strimzi Users mailing list](https://lists.cncf.io/g/cncf-strimzi-users/topics)
- [GitHub Discussions](https://github.com/orgs/strimzi/discussions)

## Contributing

You can contribute by raising any issues you find and/or fixing issues by opening Pull Requests.
All bugs, tasks or enhancements are tracked as [GitHub issues](https://github.com/strimzi/drain-cleaner/issues).

The [development documentation](./development-docs) describe how to build, test and release Strimzi Drain Cleaner.

## License

Strimzi is licensed under the [Apache License](./LICENSE), Version 2.0

## Container signatures

From the 1.0.0 release, Strimzi Drain Cleaner containers are signed using the [`cosign` tool](https://github.com/sigstore/cosign).
Strimzi currently does not use the keyless signing and the transparency log.
To verify the container, you can copy the following public key into a file:

```
-----BEGIN PUBLIC KEY-----
MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAET3OleLR7h0JqatY2KkECXhA9ZAkC
TRnbE23Wb5AzJPnpevvQ1QUEQQ5h/I4GobB7/jkGfqYkt6Ct5WOU2cc6HQ==
-----END PUBLIC KEY-----
```

And use it to verify the signature:

```
cosign verify --key strimzi.pub quay.io/strimzi/drain-cleaner:latest --insecure-ignore-tlog=true
```

## Software Bill of Materials (SBOM)

From the 1.0.0 release, Strimzi Drain Cleaner publishes the software bill of materials (SBOM) of our containers.
The SBOMs are published as an archive with `SPDX-JSON` and `Syft-Table` formats signed using cosign.
For releases, they are also pushed into the container registry.
To verify the SBOM signatures, please use the Strimzi public key:

```
-----BEGIN PUBLIC KEY-----
MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAET3OleLR7h0JqatY2KkECXhA9ZAkC
TRnbE23Wb5AzJPnpevvQ1QUEQQ5h/I4GobB7/jkGfqYkt6Ct5WOU2cc6HQ==
-----END PUBLIC KEY-----
```

You can use it to verify the signature of the SBOM files with the following command:

```
cosign verify-blob --key cosign.pub --bundle <SBOM-file>.bundle --insecure-ignore-tlog=true <SBOM-file>
```
