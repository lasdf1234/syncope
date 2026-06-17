# Data Recovery Procedure

When identity data is inconsistent between Entra, Syncope, and Gravitino, pause inbound provisioning and run controlled Pull/Push recovery tasks.

## 1. Pause Entra provisioning

1. Microsoft Entra admin center → **Identity → Applications → Enterprise applications**.
2. Select the SCIM application.
3. **Provisioning** → set status to **Off** → Save.

## 2. Orphan cleanup inbound action

`OrphanCleanupInboundActions` is preloaded on new Syncope domains under **Configuration → Implementations**. Existing databases created before this default content was added can register it manually:

```bash
curl -k -u admin:password \
  -H 'Content-Type: application/json' \
  -H 'X-Syncope-Domain: Master' \
  -X POST \
  https://<syncope-core-host>/syncope/rest/implementations/INBOUND_ACTIONS/OrphanCleanupInboundActions \
  -d '{
    "key": "OrphanCleanupInboundActions",
    "engine": "JAVA",
    "type": "INBOUND_ACTIONS",
    "body": "org.apache.syncope.core.provisioning.java.pushpull.OrphanCleanupInboundActions"
  }'
```

## 3. Register AppIdReconFilterBuilder

Register a Groovy `RECON_FILTER_BUILDER` implementation named `AppIdReconFilterBuilder` (Console or REST). Replace `APP_ID` with your Entra application client ID:

```bash
curl -k -u admin:password \
  -H 'Content-Type: application/json' \
  -H 'X-Syncope-Domain: Master' \
  -X POST \
  https://<syncope-core-host>/syncope/rest/implementations/RECON_FILTER_BUILDER/AppIdReconFilterBuilder \
  -d @- <<'EOF'
{
  "key": "AppIdReconFilterBuilder",
  "engine": "GROOVY",
  "type": "RECON_FILTER_BUILDER",
  "body": "import java.util.LinkedHashSet\nimport org.identityconnectors.framework.common.objects.AttributeBuilder\nimport org.identityconnectors.framework.common.objects.ObjectClass\nimport org.identityconnectors.framework.common.objects.OperationOptions\nimport org.identityconnectors.framework.common.objects.OperationOptionsBuilder\nimport org.identityconnectors.framework.common.objects.filter.Filter\nimport org.identityconnectors.framework.common.objects.filter.FilterBuilder\nimport org.apache.syncope.core.provisioning.api.pushpull.ReconFilterBuilder\n\nclass AppIdReconFilterBuilder implements ReconFilterBuilder {\n  private static final String APP_ID = \"replace-with-your-azure-client-id\"\n\n  @Override\n  Filter build(ObjectClass objectClass) {\n    FilterBuilder.equalTo(AttributeBuilder.build(\"appId\", APP_ID))\n  }\n\n  @Override\n  OperationOptions build(ObjectClass objectClass, OperationOptions initialOptions) {\n    def attrsToGet = new LinkedHashSet<String>()\n    if (initialOptions?.getAttributesToGet() != null) {\n      attrsToGet.addAll(initialOptions.getAttributesToGet())\n    }\n    attrsToGet.add(\"appId\")\n    new OperationOptionsBuilder(initialOptions).setAttributesToGet(attrsToGet as String[]).build()\n  }\n}"
}
EOF
```

## 4. Recovery Azure Pull

Create a dedicated Azure connector and resource for recovery (see [Azure Pull](configure-azure-pull.md)).

![Recovery pull task](images/recovery-pull-task.png)
![Create Azure connector](images/recovery-create-azure-connector.png)
![Recovery connector settings](images/recovery-connector-settings.png)

Configure **Provision Rules** and templates for USER and GROUP.

![Recovery mappings](images/recovery-provision-mappings.png)

Create a **Pull task** with `FILTERED_RECONCILIATION`, `AppIdReconFilterBuilder`, and `OrphanCleanupInboundActions` (see [Azure Pull](configure-azure-pull.md)).

![Create pull task](images/recovery-create-pull-task.png)
![Pull task configuration](images/recovery-pull-task-configuration.png)

## 5. Recovery Gravitino Push

Copy the existing Gravitino push resource and set `searchScriptFileName` to `ListSearchScript.groovy`.

![Copy resource](images/recovery-copy-resource.png)
![ListSearchScript](images/recovery-list-search-script.png)

Create and run the recovery **Push task**.

![Recovery push task](images/recovery-push-task.png)

## 6. Execution order

1. Stop third-party (Entra) provisioning.
2. Run Azure full **Pull** in Syncope (clears orphans on Syncope side).
3. Run Gravitino **Push** from Syncope (clears orphans on Gravitino side).
4. Re-enable Entra provisioning.

## Index

[Azure + Gravitino scenario](README.md)
