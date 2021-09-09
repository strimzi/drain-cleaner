# Developing Drain Cleaner

## Build

This project uses [Quarkus, the Supersonic Subatomic Java Framework](https://quarkus.io/).
It can be build directly using Maven.
But it also has a simple Make build to make it easier to build the binary and the container image.

### Running the application in dev mode

You can run the application in dev mode that enables live coding using:
```shell script
mvn compile quarkus:dev
```

### Creating a native executable using Maven

If you have [GraalVM](https://www.graalvm.org/) installed locally, you can create a native executable using: 
```shell script
mvn package -Pnative
```

Or you can run the native executable build for Linux in a container using: 
```shell script
mvn package -Pnative -Dquarkus.native.container-build=true
```

This is useful especially when running on other operating systems such as macOS or when you don't have GraalVM installed.

You can then execute your native executable with: `./target/strimzi-drain-cleaner-1.0.0-SNAPSHOT-runner`.

### Creating a native executable using Make

If you have [GraalVM](https://www.graalvm.org/) installed locally, you can create a native executable using: 
```shell script
make java_package
```

Or you can run the native executable build for Linux in a container using: 
```shell script
MVN_ARGS=-Dquarkus.native.container-build=true make java_package
```

This is useful especially when running on other operating systems such as macOS or when you don't have GraalVM installed.

You can then execute your native executable with: `./target/strimzi-drain-cleaner-1.0.0-SNAPSHOT-runner`.

### Building a container image manually

After you have the native executable, you can build the container manually:

```
docker build -f Dockerfile -t my-registry.tld/my-org/strimzi-drain-cleaner:latest .
docker push my-registry.tld/my-org/strimzi-drain-cleaner:latest
```

_Update the container image name to match your own registry / organization etc._

### Building a container image using Make

You can also build the image and push it into the registry using Make:

```
make docker_build docker_push
```

You can use the following environment variables to configure where will the image be pushed:
* `DOCKER_REGISTRY` defines the registry where it will be pushed. 
  For example `docker.io`.
* `DOCKER_ORG` defines the organization where it will be pushed. 
  For example `my-org`.
* `DOCKER_TAG` defines the tag under which will the image be pushed. 