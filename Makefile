include ./Makefile.os
include ./Makefile.docker
include ./Makefile.maven

PROJECT_NAME ?= drain-cleaner
GITHUB_VERSION ?= main
RELEASE_VERSION ?= latest

ifneq ($(RELEASE_VERSION),latest)
  GITHUB_VERSION = $(RELEASE_VERSION)
endif

.PHONY: release
release: release_prepare release_maven release_version release_pkg

release_prepare:
	rm -rf ./strimzi-drain-cleaner-$(RELEASE_VERSION)
	rm -f ./strimzi-drain-cleaner-$(RELEASE_VERSION).tar.gz
	rm -f ./strimzi-drain-cleaner-$(RELEASE_VERSION).zip
	rm -f ./strimzi-drain-cleaner-helm-3-chart-$(RELEASE_VERSION).zip
	mkdir ./strimzi-drain-cleaner-$(RELEASE_VERSION)

release_version:
	echo "Changing Docker image tags in install to :$(RELEASE_VERSION)"
	$(FIND) ./packaging/install -name '*.yaml' -type f -exec $(SED) -i '/image: "\?quay.io\/strimzi\/[a-zA-Z0-9_.-]\+:[a-zA-Z0-9_.-]\+"\?/s/:[a-zA-Z0-9_.-]\+/:$(RELEASE_VERSION)/g' {} \;
	echo "Changing Docker image tags in Helm Chart to :$(RELEASE_VERSION)"
	CHART_PATH=./packaging/helm-charts/helm3/strimzi-drain-cleaner; \
	$(SED) -i 's/\(tag: \).*/\1$(RELEASE_VERSION)/g' $$CHART_PATH/values.yaml; \
	$(SED) -i 's/\(appVersion: \).*/\1$(RELEASE_VERSION)/g' $$CHART_PATH/Chart.yaml; \
	$(SED) -i 's/\(version: \).*/\1$(RELEASE_VERSION)/g' $$CHART_PATH/Chart.yaml; \
	$(SED) -i 's/\(image.tag[^\n]*| \)`.*`/\1`$(RELEASE_VERSION)`/g' $$CHART_PATH/README.md

release_maven:
	echo "Update pom versions to $(RELEASE_VERSION)"
	mvn $(MVN_ARGS) versions:set -DnewVersion=$(shell echo $(RELEASE_VERSION) | tr a-z A-Z)
	mvn $(MVN_ARGS) versions:commit

release_pkg: helm_pkg
	$(CP) -r ./packaging/install ./
	$(CP) -r ./packaging/install ././strimzi-drain-cleaner-$(RELEASE_VERSION)/
	tar -z -cf ./strimzi-drain-cleaner-$(RELEASE_VERSION).tar.gz strimzi-drain-cleaner-$(RELEASE_VERSION)/
	zip -r ./strimzi-drain-cleaner-$(RELEASE_VERSION).zip strimzi-drain-cleaner-$(RELEASE_VERSION)/
	rm -rf ./strimzi-drain-cleaner-$(RELEASE_VERSION)
	rm -rfv ./helm-charts/helm3/strimzi-drain-cleaner
	$(FIND) ./packaging/install/ -mindepth 1 -maxdepth 1 ! -name Makefile -type f,d -exec $(CP) -rv {} ./install/ \;
	$(CP) -rv ./packaging/helm-charts/helm3/strimzi-drain-cleaner ./helm-charts/helm3/strimzi-drain-cleaner

helm_pkg:
	# Copying unarchived Helm Chart to release directory
	mkdir -p strimzi-$(RELEASE_VERSION)/helm3-charts/
	helm package --version $(RELEASE_VERSION) --app-version $(RELEASE_VERSION) --destination ./ ./packaging/helm-charts/helm3/strimzi-drain-cleaner/
	$(CP) strimzi-drain-cleaner-$(RELEASE_VERSION).tgz strimzi-drain-cleaner-helm-3-chart-$(RELEASE_VERSION).tgz
	rm -rf strimzi-$(RELEASE_VERSION)/helm3-charts/
	rm strimzi-drain-cleaner-$(RELEASE_VERSION).tgz

helm_install: packaging/helm-charts/helm3
	$(MAKE) -C packaging/helm-charts/helm3 $(MAKECMDGOALS)

.PHONY: all
all: java_package helm_install docker_build docker_push

.PHONY: clean
clean: java_clean
