#!/bin/bash

#Update dependencies for latest -SNAPSHOT



# replace ee.cyber.cdoc2:* dependency versions with latest release version (includes packages from local maven repo)
mvn -f cdoc2-server versions:use-latest-versions -Dincludes=ee.cyber.cdoc2:* -DexcludeReactor=false -DallowSnapshots=true

# put and get server have spring-boot as parent and need to be updated separately

mvn -f cdoc2-server/put-server versions:use-latest-versions -Dincludes=ee.cyber.cdoc2:* -DexcludeReactor=false -DallowSnapshots=true

mvn -f cdoc2-server/get-server versions:use-latest-versions -Dincludes=ee.cyber.cdoc2:* -DexcludeReactor=false -DallowSnapshots=true