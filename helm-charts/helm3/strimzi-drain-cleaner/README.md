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

The [development documentation](https://github.com/strimzi/drain-cleaner/tree/main/development-docs) describe how to build, test and release Strimzi Drain Cleaner.

## License

Strimzi is licensed under the [Apache License](https://github.com/strimzi/drain-cleaner/blob/main/LICENSE), Version 2.0

## Installing the Chart

To install the chart with the release name `my-strimzi-drain-cleaner`:

```bash
$ helm install my-strimzi-drain-cleaner oci://quay.io/strimzi-helm/strimzi-drain-cleaner
```

The command deploys the Strimzi Drain Cleaner on the Kubernetes cluster with the default configuration.
It expects Cert Manager to be installed to issue the TLS certificates.
The [configuration](#configuration) section lists the parameters that can be configured during installation.

## Uninstalling the Chart

To uninstall/delete the `my-strimzi-drain-cleaner` deployment:

```bash
$ helm delete my-strimzi-drain-cleaner
```

The command removes all the Kubernetes components associated with the Drain Cleaner utility and deletes the release.

## Configuration

The following table lists some available configurable parameters of the Strimzi chart and their default values.
For a full list of supported options, check the [`values.yaml` file](./values.yaml).

| Parameter                | Description                                                            | Default         |
|--------------------------|------------------------------------------------------------------------|-----------------|
| `replicaCount`           | Number of replicas of the Drain Cleaner webhook                        | 1               |
| `image.registry`         | Override default Drain Cleaner image registry                          | `quay.io`       |
| `image.repository`       | Override default Drain Cleaner image repository                        | `strimzi`       |
| `image.name`             | Drain Cleaner image name                                               | `drain-cleaner` |
| `image.tag`              | Override default Drain Cleaner image tag                               | `1.4.0`        |
| `image.imagePullPolicy`  | Image pull policy for all pods deployed by Drain Cleaner               | `nil`           |
| `image.imagePullSecrets` | List of Docker registry pull secrets                                   | `[]`            |
| `resources`              | Configures resources for the Drain Cleaner Pod                         | `[]`            |
| `tolerations`            | Add tolerations to Drain Cleaner Pod                                   | `[]`            |
| `affinity`               | Add affinities to Drain Cleaner Pod                                    | `{}`            |
| `nodeSelector`           | Add a node selector to Drain Cleaner Pod                               | `{}`            |
| `deploymentStrategy`     | Adjust the Kubernetes rollout strategy of the Drain Cleaner Deployment | `{}`            |
| `webhook.faillurePolicy` | Override default validating webhook failurePolicy                      | `Ignore`        |

Specify each parameter using the `--set key=value[,key=value]` argument to `helm install`. For example,

```bash
$ helm install my-strimzi-drain-cleaner --set replicaCount=2 oci://quay.io/strimzi-helm/strimzi-drain-cleaner
```
