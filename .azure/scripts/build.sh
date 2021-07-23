#!/usr/bin/env bash
set -e

echo "Build reason: ${BUILD_REASON}"
echo "Source branch: ${BRANCH}"

# Build with Maven
make java_package docker_build

# Push to Nexus
if [ "$BUILD_REASON" == "PullRequest" ] ; then
    echo "Building Pull Request - nothing to push"
elif [[ "$BRANCH" != "refs/tags/"* ]] && [ "$BRANCH" != "refs/heads/main" ]; then
    echo "Not in main branch or in release tag - nothing to push"
else
    echo "In main branch or in release tag - pushing to container refistry"

    echo "Login into Docker Hub ..."
    docker login -u $DOCKER_USER -p $DOCKER_PASS $DOCKER_REGISTRY

    if [ "$BRANCH" == "refs/heads/main" ]; then
        export DOCKER_TAG="latest"
    else
        export DOCKER_TAG="${BRANCH#refs/tags/}"
    fi

    make docker_tag docker_push
fi
