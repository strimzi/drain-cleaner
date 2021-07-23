include ./Makefile.docker
include ./Makefile.maven

PROJECT_NAME ?= drain-cleaner
GITHUB_VERSION ?= main
RELEASE_VERSION ?= latest

ifneq ($(RELEASE_VERSION),latest)
  GITHUB_VERSION = $(RELEASE_VERSION)
endif

.PHONY: all
all: java_package docker_build docker_push

.PHONY: clean
clean: java_clean
