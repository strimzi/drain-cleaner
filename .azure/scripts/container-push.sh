#!/usr/bin/env bash
set -e

echo "Build reason: ${BUILD_REASON}"
echo "Source branch: ${BRANCH}"

# Tag and Push the container
echo "Login into Docker Hub ..."
docker login -u $DOCKER_USER -p $DOCKER_PASS $DOCKER_REGISTRY

if [ "$BRANCH" == "refs/heads/main" ]; then
    export DOCKER_TAG="latest"
else
    export DOCKER_TAG="${BRANCH#refs/tags/}"
fi

make docker_load docker_tag docker_push
