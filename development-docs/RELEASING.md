# Releasing Drain Cleaner

This document describes how to release a new version of the Strimzi Drain Cleaner.

## Regular releases

### Create release branch

Before releasing new major or minor version of Drain Cleaner, the release branch has to be created.
The release branch should be named as `release-<Major>.<Minor>.x`.
For example for release 1.2.0, the should be named `release-1.2.x`.
The release branch is normally created from the `main` branch.
This is normally done locally and the branch is just pushed into the GitHub repository.

When releasing a new patch version, the release branch should already exist.
You just need to cherry-pick bug fixes or add them through PRs.

### Prepare the release

For any new release - major, minor or patch - we need to prepare the release.
The release preparation includes updating the installation files for the new version or changing the version of the Maven project.
This is implemented in the `Makefiles`, you just need to run the command `RELEASE_VERSION=<NewRealeaseVersion> make release`.
For example, for release 1.2.0, you would run `RELEASE_VERSION=1.2.0 make release`.

TODO: Copy the install files in Makefiles

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
It will also prepare in artifacts the ZIp and TAR.GZ archives with the installation files.
These will be later attached to the GitHub releases.

### Smoke tests

After the release pipeline is finished, it is always good idea to do some smoke tests of the images to double check they were pushed correctly.

### Creating the release

After the release pipeline is finished, the release has to be created:

* Tag the right commit from the release branch with the release name (e.g. `git tag 1.2.0`) and push it to GitHub
* On GitHub, create the release and attach the ZIP / TAR.GZ artifacts from the release pipeline to it

### Announcements

Announce the release on following channels:
* Mailing lists
* Slack
* Twitter (if the release is significant enough)

### Release candidates

Release candidates are built with the same release pipeline as the final releases.
When statrting the pipeline, use the RC name as the release veresion.
For example `1.2.0-rc1` or `1.2.0-rc2`.
For release pipelines, you should skip the suffixed build since it is not needed.

When doing the release candidates, the release branch should be already prepared for the final release.
E.g. when building `1.2.0-rc1`, the release branch should have already the `1.2.0` versions set.
The release candidate version (e.g. `1.2.0-rc1`) should be used for the GitHub tag and release.
