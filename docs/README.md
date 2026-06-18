# Apache Syncope Documentation

English guides for building, deploying, and operating Apache Syncope, including step-by-step integration scenarios.

## Directory layout

```text
docs/
  README.md                        ← you are here (Syncope docs hub)
  architecture/                    ← project overview (read before build)
  build/                           ← build and deploy (local, Docker, K8s)
  scenarios/                       ← integration scenarios
    azure-to-gravitino/               ← Azure → Syncope → Gravitino
  */images/                        ← screenshots next to each guide
```

## Guides

### Architecture (`architecture/`)

| Document                                | Description                              |
|:----------------------------------------|:-----------------------------------------|
| [overview.md](architecture/overview.md) | Runtime components and repository layout |

### Build (`build/`)

| Document                                         | Description                                                      |
|:-------------------------------------------------|:-----------------------------------------------------------------|
| [build-and-deploy.md](build/build-and-deploy.md) | Build, deploy (local / Docker / K8s), PostgreSQL, Console access |

### Scenarios (`scenarios/`)

Scenario index: [scenarios/README.md](scenarios/README.md)

#### Azure + Gravitino (`scenarios/azure-to-gravitino/`)

Scenario overview: [scenarios/azure-to-gravitino/overview.md](scenarios/azure-to-gravitino/overview.md)

| Document                                                                                                      | Description                                            |
|:--------------------------------------------------------------------------------------------------------------|:-------------------------------------------------------|
| [overview.md](scenarios/azure-to-gravitino/overview.md)                                                       | Two scenarios: SCIM → Gravitino, alert & data recovery |
| [configuration-types-azure-gravitino.md](scenarios/azure-to-gravitino/configuration-types-azure-gravitino.md) | Schemas, AnyTypeClasses, AnyTypes                      |
| [configure-scim-provisioning.md](scenarios/azure-to-gravitino/configure-scim-provisioning.md)                 | SCIM 2.0, tokens, Entra provisioning                   |
| [configure-azure-pull.md](scenarios/azure-to-gravitino/configure-azure-pull.md)                               | Entra prerequisites, Azure Pull                        |
| [configure-gravitino-push.md](scenarios/azure-to-gravitino/configure-gravitino-push.md)                       | Gravitino REST Push (connector, resources, tasks)      |
| [configure-incremental-data-import.md](scenarios/azure-to-gravitino/configure-incremental-data-import.md)     | Incremental — SCIM + Propagation + `rest-push-task`    |
| [configure-full-data-import.md](scenarios/azure-to-gravitino/configure-full-data-import.md)                   | Full import — Azure Pull + orphan cleanup push         |
| [configure-email-notifications.md](scenarios/azure-to-gravitino/configure-email-notifications.md)             | Notification job, task-failure alert rules             |

Recommended order: [Project architecture](architecture/overview.md) → [Build and deploy](build/build-and-deploy.md) → [Scenarios](scenarios/README.md).

## Assets

Screenshots live next to the guide that uses them:

| Topic                         | Location                                                                     |
|:------------------------------|:-----------------------------------------------------------------------------|
| Architecture                  | [architecture/](architecture/)                                               |
| Build                         | [build/images/](build/images/)                                               |
| Scenarios (Azure + Gravitino) | [scenarios/azure-to-gravitino/images/](scenarios/azure-to-gravitino/images/) |
