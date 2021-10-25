#!/usr/bin/env bash
set -e

echo "Build reason: ${BUILD_REASON}"
echo "Source branch: ${BRANCH}"

echo "Releasing artifacts for ${RELEASE_VERSION}"

make release
