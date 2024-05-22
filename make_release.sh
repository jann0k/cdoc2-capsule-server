#!/bin/bash

set -o xtrace
# git commit
# git tag
# git push
# mvn deploy
# docker deploy

#export GIT_REMOTE=gitlab.ext
CDOC2_SERVER_VER=$(mvn -f cdoc2-server help:evaluate -Dexpression=project.version -q -DforceStdout)

GIT_BRANCH=$(git branch --show-current)
GIT_REMOTE=$(git config --get-regexp "branch\.$GIT_BRANCH\.remote" | sed -e "s/^.* //")

if [[ "master" != "$GIT_BRANCH" ]]; then
  echo "Not on 'master' branch. You have 5 seconds to abort or the script will continue"
  sleep 5
fi


if [[ ${CDOC2_SERVER_VER} == *"SNAPSHOT"* ]];then
    echo "cdoc2-server is still on SNAPSHOT ${CDOC2_SERVER_VER}. Did you run prepare_release.sh?"
    exit 2
fi

if ! grep -q ${CDOC2_SERVER_VER} "CHANGELOG.md"; then
  echo "Can't find \"${CDOC2_SERVER_VER}\" in CHANGELOG.md. Did you update CHANGELOG.md?"
  exit 3
fi


#TODO: change release_branch name release_v<xyz>
export RELEASE_BRANCH="test_v$CDOC2_SERVER_VER"
export RELEASE_TAG="v$RELEASE_BRANCH"

git checkout -b "$RELEASE_BRANCH" || exit 1
git commit -a -m "Release cdoc2-server version $CDOC2_SERVER_VER" || exit 1
git push "$GIT_REMOTE" -u "$RELEASE_BRANCH" || exit 1
git tag "$RELEASE_TAG" || exit 1
git push --tags $GIT_REMOTE "$RELEASE_TAG" || exit 1
echo "Created release branch $RELEASE_BRANCH"

# to delete branch
# git checkout RM-3196_release_workflow
# git branch -D test_v1.2.0
# git push gitlab.ext -d test_v1.2.0

#deploy RELEASE modules
mvn -f cdoc2-server deploy -DskipTests

if [[ $? -ne 0 ]]; then
  echo "mvn deploy failed. If this was temporary error, it may be possible to recover by re-running 'mvn -f cdoc2-server deploy -DskipTests'"
fi

# switch back to original branch
git checkout $GIT_BRANCH

echo "Created release branch $RELEASE_BRANCH"
echo "To merge squash back to your branch. Run"
echo "git merge --squash $RELEASE_TAG"
echo "git commit -m \"Squashed commit from $RELEASE_TAG\""


#increase minor version and add -SNAPSHOT
#mvn -f cdoc2-shared-crypto versions:set -DnextSnapshot -DnextSnapshotIndexToIncrement=2

#build docker images?
