PROJECT_NAME=helm-charts

include ../../../Makefile.os

RELEASE_VERSION ?= latest
CHART_SEMANTIC_RELEASE_VERSION ?= $(shell cat ../../../release.version | tr A-Z a-z)
CHART_NAME=strimzi-drain-cleaner
CHART_PATH ?= ../helm3/strimzi-drain-cleaner/
CHART_RENDERED_TEMPLATES_TMP=../../../target/charts
CHART_RENDERED_TEMPLATES_CERTMANAGER_INSTALL=../../install/certmanager/
CHART_RENDERED_TEMPLATES_KUBERNETES_INSTALL=../../install/kubernetes/
CHART_RENDERED_TEMPLATES_OPENSHIFT_INSTALL=../../install/openshift/
HELM_CLI=helm

helm_clean:
	rm -rfv $(CHART_RENDERED_TEMPLATES_TMP)
	rm -rf strimzi-$(RELEASE_VERSION)/charts/
	rm -f $(CHART_NAME)-*.tgz

helm_lint:
	# Linting Helm Chart
	$(HELM_CLI) lint --debug $(CHART_PATH)

helm_certmanager_template:
	# Generating rendered template files to: $(CHART_RENDERED_TEMPLATES_TMP)
	mkdir -p $(CHART_RENDERED_TEMPLATES_TMP)
	rm -rfv $(CHART_RENDERED_TEMPLATES_TMP)
	$(HELM_CLI) template strimzi-drain-cleaner --namespace strimzi-drain-cleaner --output-dir $(CHART_RENDERED_TEMPLATES_TMP) $(CHART_PATH)

helm_certmanager_install: helm_certmanager_template
	# Remove Helm-related labels
	$(FIND) $(CHART_RENDERED_TEMPLATES_TMP) -type f -name '*.yaml' -exec $(SED) -i '/^---/d' {} \;
	$(FIND) $(CHART_RENDERED_TEMPLATES_TMP) -type f -name '*.yaml' -exec $(SED) -i '/^# Source: /d' {} \;
	$(FIND) $(CHART_RENDERED_TEMPLATES_TMP) -type f -name '*.yaml' -exec yq eval -i 'del .metadata.labels."helm.sh/chart"' {} \;
	$(FIND) $(CHART_RENDERED_TEMPLATES_TMP) -type f -name '*.yaml' -exec yq eval -i 'del .metadata.labels."app.kubernetes.io/name"' {}  \;
	$(FIND) $(CHART_RENDERED_TEMPLATES_TMP) -type f -name '*.yaml' -exec yq eval -i 'del .metadata.labels."app.kubernetes.io/instance"' {}  \;
	$(FIND) $(CHART_RENDERED_TEMPLATES_TMP) -type f -name '*.yaml' -exec yq eval -i 'del .metadata.labels."app.kubernetes.io/version"' {} \;
	$(FIND) $(CHART_RENDERED_TEMPLATES_TMP) -type f -name '*.yaml' -exec yq eval -i 'del .metadata.labels."app.kubernetes.io/managed-by"' {} \;
	# Copying rendered template files to: $(CHART_RENDERED_TEMPLATES_CERTMANAGER_INSTALL)
	mkdir -p $(CHART_RENDERED_TEMPLATES_CERTMANAGER_INSTALL)
	rm -fv $(CHART_RENDERED_TEMPLATES_CERTMANAGER_INSTALL)/*
	# Find rendered resources and move them
	$(FIND) $(CHART_RENDERED_TEMPLATES_TMP) -type f -name '*.yaml' \
	    | xargs -IFILE $(CP) FILE $(CHART_RENDERED_TEMPLATES_CERTMANAGER_INSTALL)

helm_kubernetes_template:
	# Generating rendered template files to: $(CHART_RENDERED_TEMPLATES_TMP)
	mkdir -p $(CHART_RENDERED_TEMPLATES_TMP)
	rm -rfv $(CHART_RENDERED_TEMPLATES_TMP)
	$(HELM_CLI) template strimzi-drain-cleaner --namespace strimzi-drain-cleaner --set certManager.create=false,secret.create=false --output-dir $(CHART_RENDERED_TEMPLATES_TMP) $(CHART_PATH)

helm_kubernetes_install: helm_kubernetes_template
	# Remove Helm-related labels
	$(FIND) $(CHART_RENDERED_TEMPLATES_TMP) -type f -name '*.yaml' -exec $(SED) -i '/^---/d' {} \;
	$(FIND) $(CHART_RENDERED_TEMPLATES_TMP) -type f -name '*.yaml' -exec $(SED) -i '/^# Source: /d' {} \;
	$(FIND) $(CHART_RENDERED_TEMPLATES_TMP) -type f -name '*.yaml' -exec yq eval -i 'del .metadata.labels."helm.sh/chart"' {} \;
	$(FIND) $(CHART_RENDERED_TEMPLATES_TMP) -type f -name '*.yaml' -exec yq eval -i 'del .metadata.labels."app.kubernetes.io/name"' {}  \;
	$(FIND) $(CHART_RENDERED_TEMPLATES_TMP) -type f -name '*.yaml' -exec yq eval -i 'del .metadata.labels."app.kubernetes.io/instance"' {}  \;
	$(FIND) $(CHART_RENDERED_TEMPLATES_TMP) -type f -name '*.yaml' -exec yq eval -i 'del .metadata.labels."app.kubernetes.io/version"' {} \;
	$(FIND) $(CHART_RENDERED_TEMPLATES_TMP) -type f -name '*.yaml' -exec yq eval -i 'del .metadata.labels."app.kubernetes.io/managed-by"' {} \;
	# Remove the Cert Manager annotation
	$(FIND) $(CHART_RENDERED_TEMPLATES_TMP) -type f -name '070-ValidatingWebhookConfiguration.yaml' -exec yq eval -i 'del .metadata.annotations' {} \;
	$(FIND) $(CHART_RENDERED_TEMPLATES_TMP) -type f -name '070-ValidatingWebhookConfiguration.yaml' -exec yq eval -i '.webhooks[0].clientConfig.caBundle line_comment="Add the generated certificate here"' {} \;
	# Copying rendered template files to: $(CHART_RENDERED_TEMPLATES_KUBERNETES_INSTALL)
	mkdir -p $(CHART_RENDERED_TEMPLATES_KUBERNETES_INSTALL)
	$(FIND) $(CHART_RENDERED_TEMPLATES_KUBERNETES_INSTALL) -type f ! -name 'README.md' ! -name '040-Secret.yaml' -delete
	# Find rendered resources and move them
	$(FIND) $(CHART_RENDERED_TEMPLATES_TMP) -type f -name '*.yaml' \
	    | xargs -IFILE $(CP) FILE $(CHART_RENDERED_TEMPLATES_KUBERNETES_INSTALL)

helm_openshift_template:
	# Generating rendered template files to: $(CHART_RENDERED_TEMPLATES_TMP)
	mkdir -p $(CHART_RENDERED_TEMPLATES_TMP)
	rm -rfv $(CHART_RENDERED_TEMPLATES_TMP)
	$(HELM_CLI) template strimzi-drain-cleaner --namespace strimzi-drain-cleaner --set certManager.create=false,secret.create=false --output-dir $(CHART_RENDERED_TEMPLATES_TMP) $(CHART_PATH)

helm_openshift_install: helm_openshift_template
	# Remove Helm-related labels
	$(FIND) $(CHART_RENDERED_TEMPLATES_TMP) -type f -name '*.yaml' -exec $(SED) -i '/^---/d' {} \;
	$(FIND) $(CHART_RENDERED_TEMPLATES_TMP) -type f -name '*.yaml' -exec $(SED) -i '/^# Source: /d' {} \;
	$(FIND) $(CHART_RENDERED_TEMPLATES_TMP) -type f -name '*.yaml' -exec yq eval -i 'del .metadata.labels."helm.sh/chart"' {} \;
	$(FIND) $(CHART_RENDERED_TEMPLATES_TMP) -type f -name '*.yaml' -exec yq eval -i 'del .metadata.labels."app.kubernetes.io/name"' {}  \;
	$(FIND) $(CHART_RENDERED_TEMPLATES_TMP) -type f -name '*.yaml' -exec yq eval -i 'del .metadata.labels."app.kubernetes.io/instance"' {}  \;
	$(FIND) $(CHART_RENDERED_TEMPLATES_TMP) -type f -name '*.yaml' -exec yq eval -i 'del .metadata.labels."app.kubernetes.io/version"' {} \;
	$(FIND) $(CHART_RENDERED_TEMPLATES_TMP) -type f -name '*.yaml' -exec yq eval -i 'del .metadata.labels."app.kubernetes.io/managed-by"' {} \;
	# Remove the Cert Manager annotation and add OpenShift annotations
	$(FIND) $(CHART_RENDERED_TEMPLATES_TMP) -type f -name '070-ValidatingWebhookConfiguration.yaml' -exec yq eval -i 'del .metadata.annotations."cert-manager.io/inject-ca-from"' {} \;
	$(FIND) $(CHART_RENDERED_TEMPLATES_TMP) -type f -name '070-ValidatingWebhookConfiguration.yaml' -exec yq eval -i '.metadata.annotations."service.beta.openshift.io/inject-cabundle" = "true"' {} \;
	$(FIND) $(CHART_RENDERED_TEMPLATES_TMP) -type f -name '050-Service.yaml' -exec yq eval -i '.metadata.annotations."service.beta.openshift.io/serving-cert-secret-name" = "strimzi-drain-cleaner"' {} \;
	# Copying rendered template files to: $(CHART_RENDERED_TEMPLATES_OPENSHIFT_INSTALL)
	mkdir -p $(CHART_RENDERED_TEMPLATES_OPENSHIFT_INSTALL)
	rm -fv $(CHART_RENDERED_TEMPLATES_OPENSHIFT_INSTALL)/*
	# Find rendered resources and move them
	$(FIND) $(CHART_RENDERED_TEMPLATES_TMP) -type f -name '*.yaml' \
	    | xargs -IFILE $(CP) FILE $(CHART_RENDERED_TEMPLATES_OPENSHIFT_INSTALL)

helm_install: helm_certmanager_install helm_kubernetes_install helm_openshift_install

helm_pkg: helm_clean helm_lint helm_install
	# Copying unarchived Helm Chart to release directory
	mkdir -p strimzi-$(RELEASE_VERSION)/charts/
	$(CP) -r $(CHART_PATH) strimzi-$(RELEASE_VERSION)/charts/$(CHART_NAME)
	# Packaging helm chart with semantic version: $(CHART_SEMANTIC_RELEASE_VERSION)
	$(HELM_CLI) package --version $(CHART_SEMANTIC_RELEASE_VERSION) --app-version $(CHART_SEMANTIC_RELEASE_VERSION) --destination ./ $(CHART_PATH)
	rm -rf strimzi-$(RELEASE_VERSION)

java_build: helm_pkg
java_install: java_build
docker_build: helm_pkg
docker_tag:
docker_push:

all: docker_build
clean: helm_clean

.PHONY: build clean release spotbugs