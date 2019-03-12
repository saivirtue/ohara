# Ohara

An easy-to-use visual stream processing tool based on Apache Kafka.

### Getting Started

**Start Your Own Ohara StreamApp**

For development

- you need to include the following jars in your project

```sh
gradle clean jar -PskipManager
cp ohara-common/build/libs/*.jar ohara-kafka/build/libs/*.jar ohara-streams/build/libs/*.jar  your_project
```

- sample ohara code can be found in

```sh
# for external cluster
ohara-streams/src/test/java/com/island/ohara/streams/SimpleApplicationForExternalEnv.java
# for ohara environment
ohara-streams/src/test/java/com/island/ohara/streams/SimpleApplicationForOharaEnv.java
```

For compilation

- In addition to ohara libraries, you will also need to include kafka libraries

```
<dependency>
    <groupId>org.apache.kafka</groupId>
    <artifactId>kafka-streams</artifactId>
    <version>1.0.1</version>
</dependency>
```

### Prerequisites

- JDK 1.8+
- Scala 2.12.8
- gradle 5.1+
- Node.js 8.12.0
- Yarn 1.7.0. (Note that you must install the exact version of yarn **1.7.0** as listed here or the **yarn.lock** file in Ohara manager could change when building on different machines)
- Docker 18.09+ (Official QA is on docker 18.09. Also, docker multi-stage, which is supported by Docker 17.05 or higher, is required in building ohara images. see https://docs.docker.com/develop/develop-images/multistage-build/ for more details)

### Code style check

Use this task to make sure your added code will have the same format and conventions with the rest of codebase

Note that we have this style check in early QA build.

```sh
gradle spotlessApply
```

### Apply apache license header

If you have added any new files in a PR. This task will automatically insert an Apache 2.0 license header in each one of these newly created files

Note that a file without the license header will fail at early QA build

```sh
gradle licenseApply
```

### Installation

**Running Ohara In Docker**

- Ensure your nodes (actual machines or VMs) have installed Docker 18.09+
- Download required images via `docker pull` command:
  - oharastream/broker:0.2.0
  - oharastream/zookeeper:0.2.0
  - oharastream/connect-worker:0.2.0
  - oharastream/configurator:0.2.0
  - oharastream/manager:0.2.0
  - oharastream/streamapp:0.2.0
- [Running configurator by docker](#running-configurator-by-docker)

**Running Ohara From Build**

[TODO]

### Running all backend-services by docker

Note that `com.island.ohara.demo.Backend` is deprecated and will be removed in v0.3

#### Backend image

(with 3 brokers, 3 workers, 1 mysql, 1 ftp server and 1 configurator)

```sh
docker run --rm -p 12345:12345 oharastream/backend:0.2.0 com.island.ohara.demo.Backend --configuratorPort 12345
```

- configuratorPort: bound by Configurator (default is random)
- zkPort: bound by zookeeper (default is random)
- brokersPort: bound by brokers (default is random). form: port_0,port_1
- workersPort: bound by workers (default is random). form: port_0,port_1
- dbPort: bound by mysql (default is random)
- ftpPort: bound by ftp server (default is random)
- ttl: time to terminate backend-service (default is 365 days)

The backend image is not included in production release. Hence, there is no guarantee to backend image.

#### PostgreSQL

```
docker run -d --rm --name postgresql -p 5432:5432 --env POSTGRES_DB=${DB_NAME} --env POSTGRES_USER=${USER_NAME} --env POSTGRES_PASSWORD=${PASSWORD} -it islandsystems/postgresql:9.2.24
```
* POSTGRES_DB: PostgreSQL DataBase name
* POSTGRES_USER: PostgreSQL login user name. **Note: POSTGRES_USER="user" is illegal to postgresql**
* POSTGRES_PASSWORD: PostgreSQL login password

#### FTP

```h
docker run --rm -p 10000-10011:10000-10011 oharastream/backend:0.2.0 com.island.ohara.testing.service.FtpServer --controlPort 10000 --dataPorts 10001-10011 --user ${UserName} --password ${Password} --hostname ${hostIP or hostName}
```

- controlPort: bound by FTP Server
- dataPorts: bound by data transportation in FTP Server

### Running configurator by docker

```sh
docker run --rm -p ${port}:${port} --add-host ${nodeHostName}:${nodeHostIP} oharastream/configurator:0.2.0 --port ${port} --hostname ${host} --node ${SshUserName}:${SshPassword}@${NodeHostName}:${SshPort}
```

- port: bound by Configurator (default is random)
- add-host: add a host mapping to /etc/hosts in configurator (nodeHostName:nodeHostIP)
- hostname: hostname to run configurator (defaults to 0.0.0.0)
- node: run a configurator with **pre-created** broker and zookeeper clusters (for testing purpose)

If `node` is not specified, the configurator will be running without **pre-created** zookeeper and broker clusters. You will need to create them manually
through configruator's RESTful APIs.

**NOTED:** you should prepare the broker and zookeeper images in each node where pre-created clusters will be running at if you want to use the `node` option

### Running manager by docker

```sh
docker run --rm -p 5050:5050 oharastream/manager:0.2.0 --port 5050 --configurator http://localhost:12345/v0
```

- port: bound by manager (default is 5050)
- configurator: basic form of restful API of configurator

### Running all tests

```sh
gradle clean test
```

**NOTED:** Some tests in ohara-it require "specific" env variables. Otherwise, they will be skipped.
see the source code of ohara-it for more details.

### Building project without manager

```sh
gradle clean build -PskipManager
```

### build uber jar

```sh
gradle clean uberJar -PskipManager
```

the uber jar is under ohara-assembly/build/libs/

### build and then publish artifacts to jfrog

```sh
gradle clean build -PskipManager -x test bintrayUpload -PbintrayUser=$user -PbintrayKey=$key -PdryRun=false
```

### Built With

- [Kafka](https://github.com/apache/kafka) - streaming tool
- [AKKA](https://akka.io/) - message-driven tool
- [Gradle](https://gradle.org) - dependency Management
- [SLF4J](https://www.slf4j.org/) - LOG wrapper
- [SCALALOGGING](https://github.com/typesafehub/scalalogging) - LOG wrapper
- [LOG4J](https://logging.apache.org/log4j/2.x/) - log plugin default

### Authors

- **Vito Jeng (vito@is-land.com.tw)** - leader
- **Jack Yang (jack@is-land.com.tw)** - committer
- **Chia-Ping Tsai (chia7712@is-land.com.tw)** - committer
- **Joshua_Lin (joshua@is-land.com.tw)** - committer
- **Geordie Mai (geordie@is-land.com.tw)** - committer
- **Yu-Chen Cheng (yuchen@is-land.com.tw)** - committer
- **Sam Cho (sam@is-land.com.tw)** - committer
- **Chih-Chiang Yeh (harryyeh@is-land.com.tw)** - committer
- **Harry Chiang (harry@is-land.com.tw)** - committer

### License

Ohara is an open source project and available under the Apache 2.0 License.
