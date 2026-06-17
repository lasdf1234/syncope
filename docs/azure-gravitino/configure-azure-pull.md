# Azure Pull Connector

Use the Azure ConnId connector in **Pull** mode to import users and groups from Microsoft Entra ID into Syncope.  
Primary for full reconciliation and disaster recovery; daily sync is usually handled by [SCIM inbound](configure-scim-inbound.md).

The examples below match the Docker Compose environment from [Build and deploy](../build/build-and-deploy.md): Console on **http://127.0.0.1:28080/syncope-console/**, Core REST on **http://127.0.0.1:18080/syncope/rest/**.

## Prerequisites

Complete these before creating the connector:

1. **Types** — `userExternalId` and `groupExternalId` schemas must exist and are attached to `USER` / `GROUP`. See [Configure Types](configuration-types-azure-gravitino.md).
2. **Entra app assignments** — for filtered recovery, assign the target users and groups to your Entra application (Enterprise applications → your app → **Users and groups**). The `AppIdReconFilterBuilder` filter scopes pull to objects linked to that app.

Fresh Syncope domains include `OrphanCleanupInboundActions` under **Configuration → Implementations**. Register `AppIdReconFilterBuilder` before creating `full-recovery-task` — see [Data recovery](data-recovery.md).

## Configuration order

```text
Types  →  Entra prerequisites  →  Register AppIdReconFilterBuilder
  →  Create connector  →  Create resource + Provision rules
  →  Create Pull task
```

## Topology overview

Create a dedicated connector and resource for recovery-style pulls (separate from SCIM or production connectors):

| Object | Name |
| :----- | :--- |
| Connector display name | `azure-pull-connector` |
| Resource | `full-recovery-resource` |

**Topology** → click `azure-pull-connector` → **Edit connector** (or **Add new resource** to attach a resource).

![Topology with azure-pull-connector and full-recovery-resource](images/azure-pull-topology-overview.png)

## Connector: `azure-pull-connector`

Bundle: `net.tirasa.connid.bundles.azure` → `net.tirasa.connid.bundles.azure.AzureConnector`.

![Create azure-pull-connector](images/azure-pull-create-connector.png)

### Required connector settings

On the connector configuration page, set (replace placeholders with your Entra app and service account values):

| Parameter | Example / notes |
| :-------- | :-------------- |
| Client Id | Entra app registration client ID |
| Authority | `https://login.microsoftonline.com/{tenantId}/` |
| Redirect URI | `https://login.live.com/oauth20_desktop.srf` |
| Resource URI | `https://graph.windows.net` |
| Username / Password / Domain | Service account for ROPC flow |
| Tenant ID | Entra tenant GUID |
| Client secret | App registration secret |
| Scopes | `https://graph.microsoft.com/.default` |
| User attributes to manage | `userPrincipalName`, `id`, **`mailNickname`** |
| Group attributes to manage | `displayName`, `id`, `mailEnabled`, `mailNickname`, `securityEnabled` |
| Restore deleted items | `False` (default) |

Enable **Override** on each value you set at connector level.

`mailNickname` in **User attributes to manage** is required: attribute mapping uses `userExternalId` ← `mailNickname` (Remote Key). Without it, Pull fails with `RequiredValuesMissing [userExternalId]`.

![azure-pull-connector settings](images/azure-pull-connector-settings.png)

## Entra prerequisites

Azure connector queries fail unless the tenant and app are configured correctly.

### Disable per-user MFA

Entra admin center → **Users** → **Per-user MFA** → select user → **Disable**.

![Disable Per-user MFA](images/entra-disable-per-user-mfa.png)

### Disable Security Defaults (if enforced)

**Identity → Overview → Properties → Manage security defaults** → set **Enable security defaults** to **No**.

![Disable Security Defaults](images/entra-disable-security-defaults.png)

### Allow public client flows

App registration → **Authentication** → **Advanced settings** → **Allow public client flows** = **Yes**.

![Allow public client flows](images/entra-allow-public-client-flows.png)

### Microsoft Graph application permissions

App registration → **API permissions** → add **Application permissions**:

- `Group.Read.All`
- `User.Read.All`
- `User.Read`
- `GroupMember.Read.All`
- `Directory.Read.All`

Grant admin consent after adding permissions.

![Add Microsoft Graph permissions](images/entra-add-microsoft-graph-permissions.png)

## Resource: `full-recovery-resource`

**Topology** → `azure-pull-connector` → **Add new resource** → name `full-recovery-resource` → save.

Open the new resource → **Provision rules** → add one row per object type:

Map Syncope AnyTypes to ConnId object classes:

| Object Type | Object Class |
| :---------- | :----------- |
| USER | `__ACCOUNT__` |
| GROUP | `__GROUP__` |

![full-recovery-resource provision rules](images/azure-pull-provision-rules.png)

### Attribute mappings

For each provision row, open **mapping** and configure internal ↔ external attributes.

**USER**

| Internal attribute | External attribute | Remote Key | Purpose |
| :----------------- | :----------------- | :--------- | :------ |
| `userExternalId` | `mailNickname` | Yes | Pull + Push |
| `username` | `__NAME__` | No | Pull + Push |

For Pull recovery, `userExternalId` stores the Entra **mail nickname** (not the Azure object ID used by SCIM `externalId`). Keep Remote Key enabled on `mailNickname`.

![USER attribute mappings](images/azure-pull-attribute-mappings.png)

**GROUP**

| Internal attribute | External attribute | Remote Key | Purpose |
| :----------------- | :----------------- | :--------- | :------ |
| `groupExternalId` | `__UID__` | Yes | Pull |
| `name` | `displayName` | No | Pull + Push |

## Pull tasks on `full-recovery-resource`

**Topology** → `full-recovery-resource` → **Pull tasks**.

![Pull tasks on full-recovery-resource](images/azure-pull-tasks-on-resource.png)

### `full-recovery-task` (recovery)

Used with [Data recovery](data-recovery.md) — filtered pull scoped to your Entra application, with orphan cleanup. `OrphanCleanupInboundActions` is preloaded on new domains; register `AppIdReconFilterBuilder` as described in [Data recovery](data-recovery.md):

| Setting | Value |
| :------ | :---- |
| Pull Mode | `FILTERED_RECONCILIATION` |
| Reconciliation filter builder | `AppIdReconFilterBuilder` |
| Actions | `OrphanCleanupInboundActions` |
| Matching rule | `UPDATE` |
| Unmatching rule | `PROVISION` |
| Allow create / update / delete | Yes / Yes / Yes |
| Destination Realm | `/` |

![full-recovery-task configuration](images/azure-pull-full-recovery-task-config.png)

### `pull-data-failure-test-task` (optional)

Same resource; uses **full** reconciliation without delete for failure testing:

| Setting | Value |
| :------ | :---- |
| Pull Mode | `FULL_RECONCILIATION` |
| Matching rule | `UPDATE` |
| Unmatching rule | `PROVISION` |
| Allow create / update / delete | Yes / Yes / No |

Run tasks manually from the Pull tasks panel (execute icon) or schedule them under **Engagements → Scheduled Tasks**.

## Index

[Azure + Gravitino scenario](README.md)
