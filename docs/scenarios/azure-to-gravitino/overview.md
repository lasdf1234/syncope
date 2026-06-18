# Azure + Gravitino — Overview

Syncope acts as the identity hub between **Microsoft Entra ID (Azure)** and **Apache Gravitino**.

Part of the [integration scenarios](../README.md) · [Apache Syncope documentation](../../README.md).  
Complete [Build and deploy](../../build/build-and-deploy.md) first, then follow one of the two scenarios below.

## Architecture

```mermaid
flowchart LR
  Azure["Microsoft Entra ID"]
  Syncope["Apache Syncope"]
  Gravitino["Apache Gravitino"]

  Azure -->|"Scenario 1: SCIM 2.0 provisioning"| Syncope
  Azure -->|"Scenario 2: Azure Pull (recovery)"| Syncope
  Syncope -->|"REST Push + Groovy scripts"| Gravitino
```

| Component     | Role                                                       |
|:--------------|:-----------------------------------------------------------|
| **Entra ID**  | Source of users and groups                                 |
| **Syncope**   | Identity store, provisioning engine, task orchestration    |
| **Gravitino** | Target metalake users/groups via REST API + Groovy scripts |

Reference environment (Docker Compose): Console **http://127.0.0.1:28080/syncope-console/**, Core REST **http://127.0.0.1:18080/syncope/rest/**, SCIM **http://127.0.0.1:18080/syncope/scim/v2/**.

---

## Scenario 1 — SCIM to Syncope, then write to Gravitino

**Purpose:** Day-to-day identity sync. Entra provisions users and groups into Syncope; Syncope pushes them to Gravitino.

### Flow

```text
Entra ID  ──SCIM 2.0──►  Syncope (USER / GROUP)
                              │
                              ├── Propagation on rest-push-resource (per change)
                              └── rest-push-task (batch reconcile)
                              ▼
                         Gravitino metalake
```

1. **Entra** sends create/update/delete for users and groups through SCIM.
2. **Syncope** stores entities with `userExternalId` / `groupExternalId` mapped from SCIM `externalId`.
3. **Syncope** writes to Gravitino via **Propagation** (real-time) and **`rest-push-task`** (batch reconcile) on `rest-push-resource`.

### Configuration order

| Step | Guide                                                           | What you configure                                          |
|:----:|:----------------------------------------------------------------|:------------------------------------------------------------|
|  1   | [Configure Types](configuration-types-azure-gravitino.md)       | `userExternalId`, `groupExternalId` schemas on USER / GROUP |
|  2   | [SCIM provisioning](configure-scim-provisioning.md)             | SCIM mappings, PAT token, Entra enterprise-app provisioning |
|  3   | [Gravitino REST Push](configure-gravitino-push.md)              | `rest-push-connector`, two resources, push task definitions |
|  4   | [Incremental data import](configure-incremental-data-import.md) | SCIM + Propagation + `rest-push-task`                       |
|  5   | [Email notifications](configure-email-notifications.md)         | *(optional)* Notification job, alert rules, mail template   |

### Key objects (reference domain)

| Object                         | Name                               |
|:-------------------------------|:-----------------------------------|
| REST connector                 | `rest-push-connector`              |
| Push resource (incremental)    | `rest-push-resource`               |
| Push resource (orphan cleanup) | `rest-push-delete-orphan-resource` |
| Incremental push task          | `rest-push-task`                   |
| Full-import orphan push task   | `rest-push-delete-orphan-task`     |

### Outbound (incremental import)

| Mechanism                         | Role                                    | Guide                                                                        |
|:----------------------------------|:----------------------------------------|:-----------------------------------------------------------------------------|
| **Propagation** (realm templates) | Real-time sync after each SCIM change   | [configure-incremental-data-import.md](configure-incremental-data-import.md) |
| **`rest-push-task`**              | Batch reconcile on `rest-push-resource` | [configure-incremental-data-import.md](configure-incremental-data-import.md) |

### Full import (recovery)

| Mechanism       | Role                                               | Guide                                                          |
|:----------------|:---------------------------------------------------|:---------------------------------------------------------------|
| **Azure Pull**  | `full-recovery-task` — re-import from Entra        | [configure-full-data-import.md](configure-full-data-import.md) |
| **Orphan push** | `rest-push-delete-orphan-task` — Gravitino cleanup | [configure-full-data-import.md](configure-full-data-import.md) |

Gravitino Groovy script sources: `fit/core-reference/src/test/resources/gravitino/`

---

## Scenario 2 — Alert, then data recovery

**Purpose:** When provisioning or push fails, operators receive an alert, re-import identity data from Entra into Syncope, and clean up orphans in Gravitino.

### Flow

```text
Task / propagation failure
  → Email alert ([ALERT] subject)
  → Pause Entra SCIM provisioning
  → Azure Pull (full-recovery-task) into Syncope
  → Orphan cleanup push to Gravitino (rest-push-delete-orphan-task)
  → Verify, resume SCIM provisioning
```

1. **Monitor** — Configure SMTP and `[ALERT]` notification rules so Pull, Push, propagation, and scheduled task failures send mail to operations.
2. **Alert received** — Investigate the failure (Console → task executions, propagation tasks, Core logs).
3. **Pause SCIM** — In Entra admin center, disable or pause provisioning on the Syncope enterprise application so no concurrent SCIM changes arrive during recovery.
4. **Pull from Entra** — Run `full-recovery-task` on `full-recovery-resource` (filtered reconciliation scoped to your Entra app, with orphan cleanup on the Syncope side).
5. **Gravitino orphan cleanup** — Execute `rest-push-delete-orphan-task` only. Do **not** run `rest-push-task`. The orphan task removes Gravitino users/groups that no longer exist in Syncope. See [Configure full data import](configure-full-data-import.md).
6. **Verify & resume** — Confirm task executions succeed and Gravitino metalake state is correct; re-enable Entra SCIM provisioning.

### Configuration order

| Step | Guide                                                     | What you configure                                                     |
|:----:|:----------------------------------------------------------|:-----------------------------------------------------------------------|
|  1   | [Configure Types](configuration-types-azure-gravitino.md) | Same schemas as Scenario 1                                             |
|  2   | [Gravitino REST Push](configure-gravitino-push.md)        | Push connector, resources, and tasks (Scenario 1 outbound stack)       |
|  3   | [Azure Pull connector](configure-azure-pull.md)           | `azure-pull-connector`, `full-recovery-resource`, `full-recovery-task` |
|  4   | [Full data import](configure-full-data-import.md)         | Run Azure Pull + orphan cleanup push (operational workflow)            |
|  5   | [Email notifications](configure-email-notifications.md)   | Notification job cron, `[ALERT]` rules, `taskFailureAlert` template    |

Register **`AppIdReconFilterBuilder`** under **Configuration → Implementations** before creating `full-recovery-task` (see [Azure Pull — Pull tasks](configure-azure-pull.md#pull-tasks-on-full-recovery-resource)).

### Key objects (reference domain)

| Object                   | Name                                             |
|:-------------------------|:-------------------------------------------------|
| Azure Pull connector     | `azure-pull-connector`                           |
| Recovery resource        | `full-recovery-resource`                         |
| Recovery pull task       | `full-recovery-task`                             |
| Orphan cleanup push task | `rest-push-delete-orphan-task`                   |
| Alert template           | `taskFailureAlert`                               |
| Alert notifications      | `[ALERT] Pull / Push / Propagation task failure` |

### Identifier note

SCIM provisioning stores the Azure **object ID** in `userExternalId`. Azure Pull recovery maps **`mailNickname`** into `userExternalId` instead. Plan Gravitino push mappings accordingly — see [Gravitino REST Push — Attribute mappings](configure-gravitino-push.md#attribute-mappings).

---

## All configuration guides

| Guide                                                                            | Scenario       | Description                                           |
|:---------------------------------------------------------------------------------|:---------------|:------------------------------------------------------|
| [configuration-types-azure-gravitino.md](configuration-types-azure-gravitino.md) | Both           | Schemas, AnyTypeClasses, AnyTypes                     |
| [configure-scim-provisioning.md](configure-scim-provisioning.md)                 | 1              | SCIM 2.0, PAT, Entra provisioning                     |
| [configure-gravitino-push.md](configure-gravitino-push.md)                       | Both           | REST connector, Groovy scripts, push task definitions |
| [configure-incremental-data-import.md](configure-incremental-data-import.md)     | 1              | SCIM + Propagation + `rest-push-task`                 |
| [configure-full-data-import.md](configure-full-data-import.md)                   | 2              | Azure Pull + `rest-push-delete-orphan-task`           |
| [configure-azure-pull.md](configure-azure-pull.md)                               | 2              | Entra prerequisites, pull connector, recovery task    |
| [configure-email-notifications.md](configure-email-notifications.md)             | 2 (monitoring) | Notification job, task-failure alert rules            |

Prerequisites: [Project architecture](../../architecture/overview.md) · [Build and deploy](../../build/build-and-deploy.md)

[← Back to integration scenarios](../README.md)
