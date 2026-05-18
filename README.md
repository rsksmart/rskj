# Welcome to RskJ
[![Build and Test](https://github.com/rsksmart/rskj/actions/workflows/build_and_test.yml/badge.svg)](https://github.com/rsksmart/rskj/actions/workflows/build_and_test.yml)
[![Rootstock Integration Tests](https://github.com/rsksmart/rskj/actions/workflows/rit.yml/badge.svg)](https://github.com/rsksmart/rskj/actions/workflows/rit.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=rskj&metric=alert_status)](https://sonarcloud.io/dashboard?id=rskj)
[![CodeQL](https://github.com/rsksmart/rskj/workflows/CodeQL/badge.svg)](https://github.com/rsksmart/rskj/actions?query=workflow%3ACodeQL)
[![OpenSSF Scorecard](https://api.scorecard.dev/projects/github.com/rsksmart/rskj/badge)](https://scorecard.dev/viewer/?uri=github.com/rsksmart/rskj)

# About
RskJ is a Java implementation of the Rootstock node. For more information about Rootstock, visit [rootstock.io](https://rootstock.io/). The [Rootstock white paper](https://rootstock.io/rsk-white-paper-updated.pdf) provides a complete conceptual overview of the platform.

# Generate reproducible build from branch
To be able to generate a reproducible build from the current branch, we can use the Dockerfile in the root to do so.
For example, let's consider that we are using the version 9.0.0 and version name `vetiver`.
```bash
$ docker build -t rskj/9.0.0-vetiver .
$ docker run -d --name rskj-temp rskj/9.0.0-vetiver
$ docker cp rskj-temp:/var/lib/rsk/. ./artifacts/
$ cd artifacts/
$ sha256sum *.jar *.pom
```

This will print the the sha256sum from the artifacts, for example:

```
9519ea135842d6293d027fcfeb87821dc08348f9bfa33e003d4b9b2f8372ce00  rskj-core-9.0.0-VETIVER-all.jar
03d6d058a3af6a3dac153d1609c0cc5a7461b07873b5a458e854a00b99e248e5  rskj-core-9.0.0-VETIVER-javadoc.jar
03ddd9e0a687fb9bb91743637a8267aa080e4327b86da7059485cc3ba754d5d8  rskj-core-9.0.0-VETIVER-sources.jar
460f12a543de4895e5726633ce55a8268e41a94c494487c1a26680328cb98312  rskj-core-9.0.0-VETIVER.jar
2b92ef7957248997ebe4b7581aa5a923414c31e65cd729fed64d7c8cf24a99ed  rskj-core-9.0.0-VETIVER.pom
```

# Getting Started
Information about compiling and running a Rootstock node can be found in the [Rootstock Developers Portal](https://dev.rootstock.io/).
The stable RskJ versions are published in the [Releases section](https://github.com/rsksmart/rskj/releases).

# Report Security Vulnerabilities
See the [vulnerability reporting guideline](https://github.com/rsksmart/rskj/blob/master/SECURITY.md) for details on how to
contact us to report a vulnerability.

# License
RskJ is licensed under the GNU Lesser General Public License v3.0, also included in our repository in the COPYING.LESSER file.

# Your Pledge
RskJ has been developed with the intention of fostering the progress of society. By using RskJ, you make a pledge not to use it to incur in:
- Any kind of illegal or criminal act, activity, or business;
- Any kind of act, activity, or business that requires any kind of governmental authorization or license to legally occur or exist without previously obtaining such authorization or license;
- Any kind of act, activity, or business that is expected to infringe upon intellectual property rights belonging to other people;
- Any kind of act, activity, or business involving dangerous or controlled goods or substances, including stolen goods, firearms, radioactive materials, or drugs.
Something will be considered illegal, criminal, or requiring any kind of governmental authorization or license when either the laws or regulations of the country in which you reside or the laws or regulations of the country from which you use RskJ consider it illegal, criminal, or requiring any kind of governmental authorization or license.
