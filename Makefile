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
	mkdir ./strimzi-drain-cleaner-$(RELEASE_VERSION)

release_version:
	echo "Changing Docker image tags in install to :$(RELEASE_VERSION)"
	$(FIND) ./packaging/install -name '*.yaml' -type f -exec $(SED) -i '/image: "\?quay.io\/strimzi\/[a-zA-Z0-9_.-]\+:[a-zA-Z0-9_.-]\+"\?/s/:[a-zA-Z0-9_.-]\+/:$(RELEASE_VERSION)/g' {} \;

release_maven:
	echo "Update pom versions to $(RELEASE_VERSION)"
	mvn versions:set -DnewVersion=$(shell echo $(RELEASE_VERSION) | tr a-z A-Z)
	mvn versions:commit

release_pkg:
	$(CP) -r ./packaging/install ./
	$(CP) -r ./packaging/install ././strimzi-drain-cleaner-$(RELEASE_VERSION)/
	tar -z -cf ./strimzi-drain-cleaner-$(RELEASE_VERSION).tar.gz strimzi-drain-cleaner-$(RELEASE_VERSION)/
	zip -r ./strimzi-drain-cleaner-$(RELEASE_VERSION).zip strimzi-drain-cleaner-$(RELEASE_VERSION)/
	rm -rf ./strimzi-drain-cleaner-$(RELEASE_VERSION)

.PHONY: all
all: java_package docker_build docker_push

.PHONY: clean
clean: java_clean
