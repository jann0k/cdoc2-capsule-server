#!/bin/bash

set -o xtrace
# git commit
# git tag
# git push
# mvn deploy
# docker deploy

export GIT_REMOTE=gitlab.ext
export CDOC2_SERVER_VER=$(mvn -f cdoc2-server help:evaluate -Dexpression=project.version -q -DforceStdout)

GIT_BRANCH=$(git branch --show-current)


if [[ ${CDOC2_SERVER_VER} == *"SNAPSHOT"* ]];then
    echo "cdoc2-server is still on SNAPSHOT ${CDOC2_SERVER_VER}. Did you run prepare_release.sh?"
    exit 2
fi

if ! grep -q ${CDOC2_SERVER_VER} "CHANGELOG.md"; then
  echo "Can't find \"${CDOC2_SERVER_VER}\" in CHANGELOG.md. Did you write CHANGELOG?"
  exit 3
fi



export RELEASE_BRANCH="test_v$CDOC2_SERVER_VER"

git checkout -b "$RELEASE_BRANCH"
git commit -a -m "Release cdoc2-server version $CDOC2_SERVER_VER"
git tag "$RELEASE_BRANCH"

git push --tags $GIT_REMOTE "$RELEASE_BRANCH"

# delete branch
# git checkout RM-3196_release_workflow
# git branch -D test_v1.2.0
# git push gitlab.ext -d test_v1.2.0

mvn -f cdoc2-server deploy -DskipTests

#build docker
