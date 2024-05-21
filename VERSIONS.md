# Versioning guidelines for CDOC2 project

CDOC2 modules are split between two repositories (or more in future). As all modules are not in the 
same repository, then version management becomes necessity.

To help with version management, this document tries to describe some ways to manage module versions.

Try to follow semantic versioning

## Development without release (inc_version.sh)

* Create feature branch <TASK_ID>_<short_description>
* Make changes

Before opening merge request, run `inc_versions.sh -d` (dry-run) and `inc_versions.sh`

This, will scan modules and increase module version only for changed modules that are not already on
"-SNAPSHOT" version. Changes are detected only for current branch and won't work for main branch.

* `git diff` to verify changes
* Commit, push
* Create MR

`inc_version.sh -d` will print out changed modules, but doesn't change any files. 

The script is not perfect, for example if you only change README in a module, then module is still 
considered changed although no code changes. 

## Using latest version of modules (use_latest_snapshot.sh)

After creating new version Maven module or artifact, install it locally

`mvn install`
`mvn -f <module_name> install`

### Update cdoc2 dependencies for single module

* `mvn -f <module> versions:use-latest-versions -Dincludes=ee.cyber.cdoc2:* -DexcludeReactor=false -DallowSnapshots=true`

Example: `mvn -f cdoc2-server/get-server versions:use-latest-versions -Dincludes=ee.cyber.cdoc2:* -DexcludeReactor=false -DallowSnapshots=true`


### Update cdoc2 dependencies for all modules in repository
 
* Run `use_latest_snapshot.sh` to update all modules
* `git diff` to verify changes

## Releases (prepare_release.sh and make_release.sh)

General release procedure:

* Checkout clean branch (usually 'master')
* `prepare_release.sh` (changes versions to RELEASE versions and runs tests) 
* Verify changes (`git diff`)
* Edit CHANGELOG.md 
* `make_release.sh` (`git commit; git push` RELEASE branch and `mvn deploy` RELEASE artifacts)

This will change -SNAPSHOT version to release version, update dependencies in all modules to latest 
non-SNAPSHOT version. Build, test, create release branch, push changes, deploy maven artifacts.

This will not change checked out branch as changes are pushed to release branch. 
`mvn deploy` will be executed if release branch creation was successful. 

