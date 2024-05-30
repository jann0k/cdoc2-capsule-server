#!/bin/bash

# remove -SNAPSHOT version
# update dependencies version for ee.cyber.cdoc2.* packages with non-SNAPSHOT version
# (if local maven repo includes newer modules from cdoc2-java-ref-impl) then those are also updated
# build, test, install (local maven repo)



GIT_BRANCH=$(git branch --show-current)

if [[ "master" != "$GIT_BRANCH" ]]; then
  echo "Not on 'master' branch. You have 5 seconds to abort."
  sleep 5
fi

#if [[ -n $(git cherry -v) ]]; then
#  echo "Detected unpushed commits. Exit"
#  exit 1
#fi
#
#if [[ -n $(git status --porcelain --untracked-files=no) ]]; then
#  echo "Uncommited changes detected. Exit"
#  exit 1
#fi

while getopts "v:" opt; do
  case $opt in
    v)
      echo "Changing parent pom version to: $OPTARG"
      mvn -f cdoc2-server versions:set -DnewVersion="${OPTARG}" -DupdateMatchingVersions=false
      ;;
    ?)
      echo "Invalid option: -${OPTARG}."
      exit 1
      ;;
  esac
done


# replace module -SNAPSHOT version with release version (non-SNAPSHOT)
mvn -f cdoc2-shared-crypto versions:set -DremoveSnapshot
# build and install into local maven package repository
mvn -f cdoc2-shared-crypto install

# update version for cdoc2-openapi module
# versions are not updated for cdoc2-key-capsules-openapi OpenApi specifications
# OpenApi specifications version is parsed OAS info.version
mvn -f cdoc2-openapi versions:set -DremoveSnapshot
mvn -f cdoc2-openapi install

mvn -f cdoc2-server versions:set -DremoveSnapshot
# replace ee.cyber.cdoc2:* dependency versions with latest release version (includes packages from local maven repo)
mvn -f cdoc2-server versions:use-latest-versions -Dincludes=ee.cyber.cdoc2:* -DexcludeReactor=false -DallowSnapshots=false -DallowDowngrade=true

# put and get server have spring-boot as parent and need to be updated separately
mvn -f cdoc2-server/put-server versions:set -DremoveSnapshot
mvn -f cdoc2-server/put-server versions:use-latest-versions -Dincludes=ee.cyber.cdoc2:* -DexcludeReactor=false -DallowSnapshots=false -DallowDowngrade=true
mvn -f cdoc2-server/get-server versions:set -DremoveSnapshot
mvn -f cdoc2-server/get-server versions:use-latest-versions -Dincludes=ee.cyber.cdoc2:* -DexcludeReactor=false -DallowSnapshots=false -DallowDowngrade=true

# verify and install all modules
mvn -f cdoc2-server install