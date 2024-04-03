[![gravitee.io](https://img.shields.io/static/v1?label=Available%20at&message=Gravitee.io&color=1EC9D2)](https://download.gravitee.io/#graviteeio-cockpit/plugins/connectors/)
[![license](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://github.com/gravitee-io/gravitee-cockpit-connectors/blob/master/LICENSE.txt)
[![semantic-release](https://img.shields.io/badge/semantic--release-conventional%20commits-e10079?logo=semantic-release)](https://github.com/gravitee-io/gravitee-cockpit-connectors/releases)
[![CircleCI](https://circleci.com/gh/gravitee-io/gravitee-cockpit-connectors.svg?style=svg)](https://circleci.com/gh/gravitee-io/gravitee-cockpit-connectors)

# Gravitee Cockpit Connectors

Cockpit connector can be used to connect a Gravitee API Management (APIM) or Access Management (AM) installation to
Cockpit.

Currently, the following connectors are implemented:

* Cockpit WebSocket Connector

## Requirement

The minimum requirement is :

* Maven3
* Jdk17

For user gravitee snapshot, You need to declare the following repository in your maven settings :

https://oss.sonatype.org/content/repositories/snapshots

## Building

```
$ git clone https://github.com/gravitee-io/gravitee-cockpit-connectors.git
$ cd gravitee-cockpit-connectors
$ mvn clean package
```

## Installing

Copy the gravitee-cockpit-connector-${CONNECTOR_TYPE}-${VERSION}.zip in the gravitee home plugin directory of your APIM
or AM installation.

## Configuration

> After setting the configuration properties you will have to restart your instance.

| Parameter                                     | default | Description                                                                    |
|-----------------------------------------------|---------|--------------------------------------------------------------------------------|
| cockpit.enabled                               | false   | Is cockpit connector enabled or not                                            |
| cockpit.connector.ws.endpoints                |         | An array of cockpit endpoints urls to contact.                                 |
| cockpit.connector.ws.ssl.keystore.type        | PKCS12  | The keystore type (PKCS12, PEM, JKS)                                           |
| cockpit.connector.ws.ssl.keystore.path        |         | The path to the keystore used for Mutual TLS communication with Cockpit server |
| cockpit.connector.ws.ssl.keystore.password    |         | The password to used to access to protected keys                               |
| cockpit.connector.ws.ssl.truststore.type      | PKCS12  | The truststore type (PKCS12, PEM, JKS)                                         |
| cockpit.connector.ws.ssl.truststore.path      |         | The path to the truststore used to trust the Cockpit server                    |
| cockpit.connector.ws.ssl.truststore.password  |         | The password to used to access the truststore information                      |

