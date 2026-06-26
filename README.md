jdownload
===

[![Maven Central](https://img.shields.io/maven-central/v/com.io7m.jdownload/com.io7m.jdownload.svg?style=flat-square)](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.io7m.jdownload%22)
[![Maven Central (snapshot)](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fcentral.sonatype.com%2Frepository%2Fmaven-snapshots%2Fcom%2Fio7m%2Fjdownload%2Fcom.io7m.jdownload%2Fmaven-metadata.xml&style=flat-square)](https://central.sonatype.com/repository/maven-snapshots/com/io7m/jdownload/)
[![Codecov](https://img.shields.io/codecov/c/github/io7m-com/jdownload.svg?style=flat-square)](https://codecov.io/gh/io7m-com/jdownload)
![Java Version](https://img.shields.io/badge/21-java?label=java&color=e6c35c)

![com.io7m.jdownload](./src/site/resources/jdownload.jpg?raw=true)

| JVM | Platform | Status |
|-----|----------|--------|
| OpenJDK (Temurin) Current | Linux | [![Build (OpenJDK (Temurin) Current, Linux)](https://img.shields.io/github/actions/workflow/status/io7m-com/jdownload/main.linux.temurin.current.yml)](https://www.github.com/io7m-com/jdownload/actions?query=workflow%3Amain.linux.temurin.current)|
| OpenJDK (Temurin) LTS | Linux | [![Build (OpenJDK (Temurin) LTS, Linux)](https://img.shields.io/github/actions/workflow/status/io7m-com/jdownload/main.linux.temurin.lts.yml)](https://www.github.com/io7m-com/jdownload/actions?query=workflow%3Amain.linux.temurin.lts)|
| OpenJDK (Temurin) Current | Windows | [![Build (OpenJDK (Temurin) Current, Windows)](https://img.shields.io/github/actions/workflow/status/io7m-com/jdownload/main.windows.temurin.current.yml)](https://www.github.com/io7m-com/jdownload/actions?query=workflow%3Amain.windows.temurin.current)|
| OpenJDK (Temurin) LTS | Windows | [![Build (OpenJDK (Temurin) LTS, Windows)](https://img.shields.io/github/actions/workflow/status/io7m-com/jdownload/main.windows.temurin.lts.yml)](https://www.github.com/io7m-com/jdownload/actions?query=workflow%3Amain.windows.temurin.lts)|

## Repository Relocation

Development of this project has moved to an
[open-source but not open-contribution](https://sqlite.org/copyright.html#notopencontrib)
model.

Source code and commits will remain publicly available perpetually, but issues
and/or pull requests will be rejected and/or ignored. Additionally, this project
will now only be available via a read-only mirror at:

  https://codeberg.org/io7m-com/jdownload


## jdownload

The `jdownload` package implements a trivial wrapper around the JDK HTTP client
providing downloads, checksum verification, and transfer statistics.

## Features

* Trivial HTTP file downloader providing transfer statistics.
* Automatic checksum verification for downloads.
* Low dependency: Requires Java 21 and uses [SLF4J](https://www.slf4j.org/)
  for logging.
* High coverage test suite.
* [OSGi-ready](https://www.osgi.org/).
* [JPMS-ready](https://en.wikipedia.org/wiki/Java_Platform_Module_System).
* ISC license.

## Usage

```
HttpClient client;
URI targetURI;
Path outputFile;
Path outputFileTemp;
```

Download a file from `targetURI`, writing the data temporarily to
`outputFileTemp` and then atomically replacing `outputFile` with
`outputFileTemp` if the download succeeds:

```
final var result =
  JDownloadRequests.builder(client, targetURI, outputFile, outputFileTemp)
    .build()
    .execute();
```

Perform the same download operation, but receive transfer statistics during
the download:

```
Consumer<STTransferStatistics> receiver;

final var result =
  JDownloadRequests.builder(client, targetURI, outputFile, outputFileTemp)
    .setTransferStatisticsReceiver(receiver)
    .build()
    .execute();
```

Perform the same download operation, but reject the download if the resulting
file does not match the given checksum:

```
final byte[] checksum =
  HexFormat.of()
   .parseHex("2d8bd7d9bb5f85ba643f0110d50cb506a1fe439e769a22503193ea6046bb87f7");

final var result =
  JDownloadRequests.builder(client, targetURI, outputFile, outputFileTemp)
    .setChecksumStatically("SHA-256", checksum)
    .build()
    .execute();
```

Perform the same download operation, but reject the download if the resulting
file does not match the checksum obtained from the given checksum URI:

```
Consumer<STTransferStatistics> receiver;
Path checksumFile;
Path checksumFileTemp;
URI checksumURI;

final var result =
  JDownloadRequests.builder(client, targetURI, outputFile, outputFileTemp)
    .setChecksumFromURL(checksumURI, "SHA-256", checksumFile, checksumFileTemp, receiver)
    .build()
    .execute();
```

