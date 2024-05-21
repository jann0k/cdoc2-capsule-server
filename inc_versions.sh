#!/bin/bash

# Increase maven module version for changed modules based on git diff.

# Based on
# https://gist.github.com/corneil/20585cb944615abb434063b8507d2d8d


function itemInModules() {
  local e
  for e in ${MODULES}; do
    if [[ "$e" == "$ITEM" ]]; then
      echo "1"
      return 0
    fi
  done
  echo "0"
}
function addItem() {
    if [ "$MODULES" == "" ]; then
      echo "$1"
    else
      echo "$MODULES $1"
    fi
}

PERFORM=true

while getopts "":d"" opt; do
  case ${opt} in
    d)
      echo "-d Dry-run mode"
      PERFORM=false
      ;;
    ?)
      echo "Invalid option: -${OPTARG}."
      exit 1
      ;;
  esac
done

# Find parent branch
#https://gist.github.com/joechrysler/6073741
#https://stackoverflow.com/questions/3161204/how-to-find-the-nearest-parent-of-a-git-branch?noredirect=1&lq=1
PARENT_BRANCH=$(git show-branch -a \
                | grep '\*' \
                | grep -v `git rev-parse --abbrev-ref HEAD` \
                | head -n1 \
                | sed 's/.*\[\(.*\)\].*/\1/' \
                | sed 's/[\^~].*//')
echo
echo "Using $PARENT_BRANCH as parent branch"
echo


MODIFIED=$(git diff --name-only "$PARENT_BRANCH")
ALL_MODULES=$(find . -name "pom.xml" -type f -exec dirname '{}' \; | sed 's/\.\///' | sort -r)
MODULES=
for file in $MODIFIED; do
  FILE=$(realpath $file)
  echo "$file was changed"
  for ITEM in $ALL_MODULES; do
    if [[ "$ITEM" != "." ]] && [[ "$file" == *"$ITEM"* ]]; then
      echo "Matched $ITEM"
      HAS_ITEM=$(itemInModules)
      if ((HAS_ITEM == 0)); then
        MODULES=$(addItem "$ITEM")
      fi
      break
    fi
  done
done

#echo "All modules: $ALL_MODULES"
echo
echo "Changed modules: $MODULES"
echo
for module in $MODULES; do

  if [[ "$PERFORM" = true ]]; then
    MODULE_VERSION=$(mvn -f $ITEM help:evaluate -Dexpression=project.version -q -DforceStdout)
    if [[ ${MODULE_VERSION} == *"SNAPSHOT"* ]];then
        echo "Ignoring $ITEM as it already on SNAPSHOT "
    else
        echo "Creating -SNAPSHOT minor version for $ITEM;"
        mvn -f "$ITEM" versions:set -DnextSnapshot -DnextSnapshotIndexToIncrement=2
    fi
  else
    echo "Dry-run. Not increasing version for $ITEM."
  fi
done