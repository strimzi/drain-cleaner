#!/usr/bin/env bash
set -e

echo "Build reason: ${BUILD_REASON}"
echo "Source branch: ${BRANCH}"

make helm_install

CHANGED_DERIVED=$(git diff --name-status -- packaging/install/ packaging/helm-charts/)
GENERATED_FILES=$(git ls-files --other --exclude-standard -- packaging/install/ packaging/helm-charts/)
if [ -n "$CHANGED_DERIVED" ] || [ -n "$GENERATED_FILES" ] ; then
    if [ -n "$CHANGED_DERIVED" ] ; then
        echo "ERROR: Uncommitted changes in derived resources:"
        echo "$CHANGED_DERIVED"
    fi
  
    if [ -n "$GENERATED_FILES" ] ; then
        echo "ERROR: Uncommitted changes in generated resources:"
        echo "$GENERATED_FILES"
    fi
  
    echo "Run the following to add up-to-date resources:"
    echo "  make helm_install \\"
    echo "    && git add packaging/install/ packaging/helm-charts/ \\"
    echo "    && git commit -s -m 'Update derived resources'"
    exit 1
fi
