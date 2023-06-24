#!/usr/bin/env bash
set -x

TEST_HELM3_VERSION=${TEST_HELM3_VERSION:-'v3.12.0'}

function install_helm3 {
    export HELM_INSTALL_DIR=/usr/bin
    curl https://raw.githubusercontent.com/kubernetes/helm/master/scripts/get > get_helm.sh
    # we need to modify the script with a different path because on the Azure pipelines the HELM_INSTALL_DIR env var is not honoured
    sed -i 's#/usr/local/bin#/usr/bin#g' get_helm.sh
    chmod 700 get_helm.sh

    echo "Installing helm 3..."
    sudo ./get_helm.sh --version "${TEST_HELM3_VERSION}"

    echo "Verifying the installation of helm binary..."
    # run a proper helm command instead of, for example, "which helm", to verify that we can call the binary
    helm --help
    helmCommandOutput=$?

    if [ $helmCommandOutput != 0 ]; then
        echo "helm binary hasn't been installed properly - exiting..."
        exit 1
    fi
}

install_helm3
