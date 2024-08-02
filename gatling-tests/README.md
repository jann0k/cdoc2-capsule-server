# Gatling tests for CDOC2 server

## Preconditions for executing tests

* The latest cdoc2 java libraries are installed locally. From cdoc2-server directory run:

```
mvn clean install
```

* CDOC key servers are is running
  * Can be started inside cdoc2-server catalog with commands:
```
cd get-server
java -Dspring.config.location=config/application-local.properties -jar target/cdoc2-get-server-VER.jar
```

```
cd put-server
java -Dspring.config.location=config/application-local.properties -jar target/cdoc2-put-server-VER.jar
```

Or run servers with `-Dlogging.config=target/test-classes/logback.xml` if you need to see logs.


## Configuration

In configuration file one can specify following parameters
* Target server URL
* Client certificate information (generated automatically, used for accessing server while getting capsules)
  * location for generated client key stores (make sure specified location folder exists)
  * password for key-stores
  * alias for key-store files
* Load test configuration
  * Create and upload capsule
    * start-users-per-second - initial number of users added right after test start (e.g 5)
    * increment-users-per-second - number of users added to concurrent amount of users per second (5)
      increment-cycles - how many times number of concurrent users is incremented (3 -> 5>10>15>20)
      cycle-duration-seconds duration of each cycle with currently reached number of concurrent users (10 -> 10s with 5 concurrent users; 10s with 10 concurrent users; 10s with 15 concurrent users; 10s with 20 concurrent users - total duration time = increment cycles x cycle-duration-time)
  * Get capsule
    * initial-delay-seconds - in addition to previous parameters delay is used for allowing generating and uploading capsules

Create a configuration file using the sample file:

```
cp src/test/resources/application.conf.sample src/test/resources/application.conf
```

## Generate TLS certificates for connecting to cdoc2 server

From gatling-tests directory run:
```
mvn clean compile exec:java -Damount=10
```

Or using a compiled jar:

```
export JAVA_OPTS="-Doutput-dir=/tmp -Dkeystore-password=secret -Dkey-alias=client-key -Droot-keystore=/tmp/gatling-ca.p12 -Droot-keystore-password=secret -Droot-key-alias=gatling-ca -Damount=3"
java $JAVA_OPTS -cp target/gatling-tests-VER.jar ee.cyber.cdoc2.server.datagen.KeyStoreGenerator
```


This will generate 10 key stores (the ouput folder and other parameters are configured in pom.xml)
with private keys and certificates that can be used later in Gatling tests.
The number of generated key stores is specified by the `amount` system property.


## Running functional tests

The following functional tests exist for testing {SERVER_NAME} server functionality
* create and upload capsule to server (createAndGetCreateEccCapsule)
* Get successfully capsule from server (createAndGetCreateEccCapsule)
* Get capsule with incorrect transactionId (createAndGetRecipientTransactionMismatch)
* Get capsule with invalid transactionId (getWithInvalidTransactionIds)

A CDOC server must be running on the host:port as configured in the configuration file specified above.

From gatling-tests directory run:
```
mvn gatling:test -Dgatling.simulationClass=ee.cyber.cdoc2.server.KeyCapsuleFunctionalTests
```

Or using a compiled jar:

```
export JAVA_OPTS="-Dconfig.file=src/test/resources/application.conf -Dlogback.configurationFile=src/test/resources/logback-test.xml"
java $JAVA_OPTS -cp target/gatling-tests-VER.jar io.gatling.app.Gatling -s ee.cyber.cdoc2.server.KeyCapsuleFunctionalTests
```

## Running load tests

For running load tests first execution profile should be designed and configured. Load test execution models and configuring options are described in more detail here https://gatling.io/docs/gatling/reference/current/core/injection/#incrementuserspersec

Open Model is implemented for CDOC2 server load tests, meaning that continuously growing load is applied to the server.

* Sample configuration for continuously growing load example:
  - start-users-per-second = 10
  - increment-users-per-second = 5
  - increment-cycles = 10
  - cycle-duration-seconds = 60


  Test lasts 10 cycles x 60 seconds = 600 seconds = 10 minutes.
  Each next test cycle has 5 concurrent users more. Last cycle will have 60 concurrent users for 1 minute.


* Sample configuration for relatively stable slowly growing load:

  - start-users-per-second = 100
  - increment-users-per-second = 2
  - increment-cycles = 10
  - cycle-duration-seconds = 60

Test lasts 10 cycles x 60 seconds = 600 seconds = 10 minutes.
Initial load - 100 concurrent users will be applied and each next test cycle has 2 concurrent users more. Last cycle will have 120 concurrent users for 1 minute.

For executing load tests run from gatling-tests directory:

```
mvn gatling:test -Dgatling.simulationClass=ee.cyber.cdoc2.server.KeyCapsuleLoadTests
```

Or using a compiled jar:

```
export JAVA_OPTS="-Dconfig.file=src/test/resources/application.conf -Dlogback.configurationFile=src/test/resources/logback-test.xml"
java $JAVA_OPTS -cp target/gatling-tests-VER.jar io.gatling.app.Gatling -s ee.cyber.cdoc2.server.KeyCapsuleLoadTests
```


## Server Keystore configuration

Note: Certificates are already generated in development phase and added to Server trust store. Following commands are useful once new trust chain needs to be added to the server.

The generated certificates are signed with a test CA cert that was created with:

```
keytool -genkeypair -alias gatling-ca -keyalg ec -groupname secp384r1 -sigalg SHA512withECDSA -keystore gatling-ca.p12 -storepass secret -ext KeyUsage=digitalSignature,keyCertSign -ext BasicConstraints=ca:true,PathLen:3 -validity 365
```

To export the test CA certificate from the test CA keystore:

```
keytool -exportcert -keystore gatling-ca.p12 -alias gatling-ca -storepass secret -rfc -file gatling-ca.pem
```

To add the test CA certificate to the server's truststore:

```
keytool -import -trustcacerts -file gatling-ca.pem -alias gatling-ca -storepass passwd -keystore path/to/servertruststore.jks
```
