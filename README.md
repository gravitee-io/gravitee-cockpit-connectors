[![Build Status](https://ci2.gravitee.io/buildStatus/icon?job=gravitee-io/gravitee-cockpit-connectors/master)](https://ci2.gravitee.io/job/gravitee-io/job/gravitee-cockpit-connectors/job/master/)

# Gravitee Cockpit Connectors

Cockpit connector can be used to connect a Gravitee API Management (APIM) or Access Management (AM) installation to Cockpit.

Currently the following connectors are implemented:
 * Cockpit WebSocket Connector 

## Requirement

The minimum requirement is :
 * Maven3 
 * Jdk11

For user gravitee snapshot, You need the declare the following repository in you maven settings :

https://oss.sonatype.org/content/repositories/snapshots

## Building

```
$ git clone https://github.com/gravitee-io/gravitee-cockpit-connectors.git
$ cd gravitee-cockpit-connectors
$ mvn clean package
```

## Installing

Copy the gravitee-cockpit-connector-${CONNECTOR_TYPE}-${VERSION}.zip in the gravitee home plugin directory of your APIM or AM installation.

## Configuration

Some configuration properties need to be defined in order to make the connector work.

---
**NOTE**

After setting the configuration properties you will have to restart your instance.

---

### Common connector configuration

| Parameter                                        |   default  | Description |
| ------------------------------------------------ | ---------- | ----------- |
| cockpit.enabled                                  |   false    |  Is cockpit connector enabled or not |
| cockpit.keystore.type                            |   PKCS12   | The keystore type (PKCS12, PEM, JKS) |
| cockpit.keystore.path                            |            | The path to the keystore used for Mutual TLS communication with Cockpit server |
| cockpit.keystore.password                        |            | The password to used to access to protected keys |
| cockpit.truststore.type                          |   PKCS12   | The truststore type (PKCS12, PEM, JKS) |
| cockpit.truststore.path                          |            | The path to the truststore used to trust the Cockpit server |
| cockpit.truststore.password                      |            | The password to used to access the truststore information |

### WebSocket connector (ws)


| Parameter                                        |   default  | Description |
| ------------------------------------------------ | ---------- | ----------- |
| cockpit.ws.endpoints                             |       |   An array of cockpit endpoints urls to contact. |
