# Project Architecture

Overview of the Apache Syncope codebase in this repository: **what runs**, **how components relate**, and **where code lives**. For build commands, deployment paths, and configuration, see [Build and deploy](../build/build-and-deploy.md).

Part of the [Apache Syncope documentation](../README.md).

---

## What this project is

Apache Syncope **4.1.x** is an identity management and provisioning platform. It stores users, groups, and related data in a relational database, exposes a REST **Core** API, and ships web applications for administration and self-service.

External systems connect through **ConnId connectors** and Syncope **tasks** (pull, push, live sync). The [Azure + Gravitino](../scenarios/azure-to-gravitino/overview.md) guide documents the primary integration scenario in this fork.

---

## Runtime components

### Core, Console, Enduser

These three are the default runtime stack documented in [Build and deploy](../build/build-and-deploy.md).

| Component   | Role                                                                           | Context path (typical) |
|:------------|:-------------------------------------------------------------------------------|:-----------------------|
| **Core**    | Identity store, provisioning engine, ConnId runtime, REST API, scheduled tasks | `/syncope/`            |
| **Console** | Admin UI: types, realms, connectors, resources, tasks                          | `/syncope-console/`    |
| **Enduser** | Self-service UI: profile, password, registration (optional)                    | `/syncope-enduser/`    |

**Core** is the only component that talks to the database and external connectors. **Console** and **Enduser** are Wicket web apps that call Core over REST; they do not provision to external systems on their own.

#### Keymaster

Syncope runs as separate applications. **Keymaster** is the **service registry**: components register their network address at startup; Console and Enduser resolve Core’s address through Keymaster instead of hard-coding a URL. Keymaster also holds shared configuration (for example domain definitions) used across components.

| Mode                              | Provided by                                  | Used by                                                                 |
|:----------------------------------|:---------------------------------------------|:------------------------------------------------------------------------|
| **Self Keymaster** (typical here) | **Core** — REST at `/syncope/rest/keymaster` | Core registers itself; **Console** and **Enduser** query Core’s address |
| **ZooKeeper Keymaster**           | Separate Apache ZooKeeper registry           | Core, Console, Enduser (and optionally WA/SRA)                          |

In Self Keymaster deployments, the feature is **implemented in Core**, but **Core, Console, and Enduser all need Keymaster settings** (address and credentials): Core exposes and registers with the registry; Console and Enduser are clients that find Core through it. There is no separate Keymaster container in the default Docker Compose or Helm guides.

Property names (`KEYMASTER_ADDRESS`, `SERVICE_DISCOVERY_ADDRESS`, and related values) are documented in [Build and deploy](../build/build-and-deploy.md).

Code: `common/keymaster/` (client API), `core/self-keymaster-starter/` (Self Keymaster in Core).

### WA and SRA (optional)

Access-management components; not part of the default Compose or Helm guides in this repository.

| Component                      | Role                                                                |
|:-------------------------------|:--------------------------------------------------------------------|
| **WA** (Web Access)            | SSO and authentication hub (Apereo CAS: CAS, OIDC, SAML)            |
| **SRA** (Secure Remote Access) | Security-enabled reverse proxy / API gateway (Spring Cloud Gateway) |

Use WA/SRA when you need enterprise single sign-on or to front legacy applications with standard protocols—not for basic IdM connector workflows alone.

---

## Repository layout (high level)

Maven multi-module project. Paths are relative to the repository root (directory containing top-level `pom.xml`).

```text
pom.xml                # root POM
core/                  # Core engine, persistence, REST
client/                # Console and Enduser (Wicket UI)
standalone/            # Tomcat ZIP assembly
docker/                # Container images, Compose, Helm charts
fit/                   # Reference configuration and ConnId bundle paths
ext/                   # Extensions (SCIM, Flowable, SAML, OIDC, …)
ConnIdAzureBundle/     # Azure ConnId connector bundle
ConnIdRESTBundle/      # REST ConnId connector bundle
wa/                    # Web Access (optional)
sra/                   # Secure Remote Access (optional)
docs/                  # Documentation
```

| Runtime | Code mainly in           | Docker image (when built) |
|:--------|:-------------------------|:--------------------------|
| Core    | `core/`, `ext/`          | `apache/syncope`          |
| Console | `client/idrepo/console/` | `apache/syncope-console`  |
| Enduser | `client/idrepo/enduser/` | `apache/syncope-enduser`  |
| WA      | `wa/`                    | `apache/syncope-wa`       |
| SRA     | `sra/`                   | `apache/syncope-sra`      |

---

## Next step

[Build and deploy](../build/build-and-deploy.md) — JDK versions, build artifacts, deploy on Local, Docker, or Kubernetes.

[← Back to documentation index](../README.md)
