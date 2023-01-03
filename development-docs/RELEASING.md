# Releasing Drain Cleaner

This document describes how to release a new version of the Strimzi Drain Cleaner.

## Regular releases

### Create release branch

Before releasing new major or minor version of Drain Cleaner, the release branch has to be created.
The release branch should be named as `release-<Major>.<Minor>.x`.
For example for release 1.2.0, the branch should be named `release-1.2.x`.
The release branch is normally created from the `main` branch.
This is normally done locally and the branch is just pushed into the GitHub repository.

After creating the release branch, you should also bump the project version in the `main` branch to the next minor / major version with the `-SNAPSHOT` suffix.
You can use `make` for it:
```bash
RELEASE_VERSION=1.3.0-SNAPSHOT make release_maven 
```

When releasing a new patch version, the release branch should already exist and you do not need to bump the project version in the `main` branch.
You just need to cherry-pick bug fixes or add them through PRs.

### Prepare the release

For any new release - major, minor or patch - we need to prepare the release.
The release preparation includes updating the installation files for the new version or changing the version of the Maven project.
This is implemented in the `Makefiles`, you just need to run the command `RELEASE_VERSION=<NewRealeaseVersion> make release`.
For example, for release 1.2.0, you would run `RELEASE_VERSION=1.2.0 make release`.

Review and commit the changes done by the `make` command and push them into the repository.
The build pipeline should automatically start for any new commit pushed into the release branch.

### Running the release pipeline

Wait until the build pipeline is (successfully) finished for the last commit in the release branch.
Then run the release pipeline manually from the Azure Pipelines UI.
The release pipeline is names `drain-cleaner-release`.
When starting the new run, it will ask for several parameters which you need to fill:

* Release version (for example `1.2.0`)
* Release suffix (for example `0` - it is used to create the suffixed images such as `strimzi/drain-cleaner:1.2.0-0` to identify different builds done for example due to base image CVEs)
* Source pipeline ID (Currently, only the build pipeline with ID `36` can be used)
* Source build ID (the ID of the build from which the artifacts should be used - use the long build ID from the URL and not the shorter build number)

The release pipeline will push the images to the registry.
It will also prepare in artifacts the ZIP and TAR.GZ archives with the installation files and with the Helm Chart.
These will be later attached to the GitHub releases.

### Smoke tests

After the release pipeline is finished, it is always good idea to do some smoke tests of the images to double check they were pushed correctly.

### Creating the release

After the release pipeline is finished, the release has to be created:

* Tag the right commit from the release branch with the release name (e.g. `git tag 1.2.0`) and push it to GitHub
* On GitHub, create the release and attach the ZIP / TAR.GZ artifacts from the release pipeline to it
* Add the Helm Chart to the
    * [`index.yaml` file](https://github.com/strimzi/strimzi.github.io/blob/main/charts/index.yaml) on the Strimzi website
    * [`index.yaml` file](https://github.com/strimzi/strimzi-kafka-operator/blob/main/packaging/helm-charts/index.yaml) in the Strimzi operators repository
* Update the `./install` and `./helm-charts` directories in the `main` branch with the newly released files from the release branch
* Update the Drain Cleaner installation files in the Strimzi operators repository

### Announcements

Announce the release on following channels:
* Mailing lists
* Slack
* Twitter (if the release is significant enough)

### Release candidates

Release candidates are built with the same release pipeline as the final releases.
When starting the pipeline, use the RC name as the release version.
For example `1.2.0-rc1` or `1.2.0-rc2`.
For release pipelines, you should skip the suffixed build since it is not needed.

When doing the release candidates, the release branch should be already prepared for the final release.
E.g. when building `1.2.0-rc1`, the release branch should have already the `1.2.0` versions set.
The release candidate version (e.g. `1.2.0-rc1`) should be used for the GitHub tag and release.

## Rebuilding container images for base image CVEs

In case of a CVE in the base container image, we might need to rebuild the Drain Cleaner container image.
This can be done using the `drain-cleaner-cve-rebuild` pipeline.
This pipeline will take a previously build binaries and and use them to build a new container image.
It will also automatically run the system tests and push the container image to the container registry with the suffixed tag (e.g. `1.2.0-2`).
Afterwards, it will wait for manual approval.
This gives additional time to manually test the new container image.
After the manual approval, the image will be also pushed under the tag without suffix (e.g. `1.2.0`).

The suffix can be specified when starting the re-build pipeline.
You should always check what was the previous suffix and increment it.
That way, the older images will be still available in the container registry under their own suffixes.
But only the latest rebuild will be available under the un-suffixed tag.

When starting the pipeline, it will ask for several parameters which you need to fill:

* Release version (for example `1.2.0`)
* Release suffix (for example `0` - it is used to create the suffixed images such as `strimzi/drain-cleaner:1.2.0-2` to identify different builds done for different CVEs)
* Source pipeline ID (Currently, only the build pipeline with ID `36` can be used)
* Source build ID (the ID of the build from which the artifacts should be used - use the long build ID from the URL and not the shorter build number)

This process should be used only for CVEs in the base images.
Any CVEs in our code or in the Java dependencies require new patch (or minor) release.