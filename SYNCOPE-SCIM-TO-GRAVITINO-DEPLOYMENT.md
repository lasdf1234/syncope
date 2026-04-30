# Syncope SCIM to Gravitino Deployment Guide

## 1. Overview

This document explains how to build, deploy, configure, and operate Syncope for a SCIM-to-Gravitino integration scenario.

- **Repository:** `https://github.com/datastrato/scim-server`
- **Java version:** `jdk25`
- **Branch:** `4_1_x_gravitino`

## 2. Build and Packaging

Run the following command from the project root:

```bash
mvn -pl standalone -am clean verify -Dcheckstyle.skip=true -DskipTests -Dmaven.build.cache.enabled=false
```

After the build finishes, the distribution package is generated at:

```text
standalone/target/syncope-standalone-4.1.1-SNAPSHOT-distribution.zip
```

Extract the ZIP file to obtain the full Syncope Tomcat distribution.

![Build and package output](docs-images/scim-to-gravitino/image2.png)

## 3. Basic Runtime Preparation

### 3.1 Hosts configuration

Update `/etc/hosts`:

```text
127.0.0.1 localhost
127.0.0.1 local
```

### 3.2 Main configuration directory

```text
syncope/syncope-standalone-4.1.1-SNAPSHOT/apache-tomcat-10.1.54/webapps/syncope/WEB-INF/classes
```

## 4. MySQL Configuration

Syncope supports PostgreSQL, MySQL, Oracle, and MariaDB. This guide uses **MySQL**.

### 4.1 Update `core.properties`

Replace the default PostgreSQL settings with MySQL settings like the following:

```properties
persistence.db-type=MYSQL

persistence.domain[0].key=Master
persistence.domain[0].jdbcDriver=com.mysql.cj.jdbc.Driver
persistence.domain[0].jdbcURL=jdbc:mysql://127.0.0.1:3306/syncope?useSSL=false&characterEncoding=UTF-8&allowPublicKeyRetrieval=true&nullDatabaseMeansCurrent=true
persistence.domain[0].dbUsername=syncope
persistence.domain[0].dbPassword=syncope
persistence.domain[0].databasePlatform=org.apache.openjpa.jdbc.sql.MySQLDictionary(blobTypeName=LONGBLOB,dateFractionDigits=3,useSetStringForClobs=true)
persistence.domain[0].orm=META-INF/mysql/spring-orm.xml
persistence.domain[0].poolMaxActive=20
persistence.domain[0].poolMinIdle=5

persistence.domain[1].key=Two
persistence.domain[1].jdbcDriver=com.mysql.cj.jdbc.Driver
persistence.domain[1].jdbcURL=jdbc:mysql://127.0.0.1:3306/syncopetwo?useSSL=false&characterEncoding=UTF-8&allowPublicKeyRetrieval=true&nullDatabaseMeansCurrent=true
persistence.domain[1].dbUsername=syncope
persistence.domain[1].dbPassword=syncope
persistence.domain[1].databasePlatform=org.apache.openjpa.jdbc.sql.MySQLDictionary(blobTypeName=LONGBLOB,dateFractionDigits=3,useSetStringForClobs=true)
persistence.domain[1].orm=META-INF/mysql/spring-orm.xml
persistence.domain[1].poolMaxActive=20
persistence.domain[1].poolMinIdle=5
```

### 4.2 Update `core-embedded.properties`

Remove embedded / PostgreSQL database settings and keep the MySQL-oriented domain configuration.

### 4.3 Add the MySQL JDBC driver

Copy the MySQL driver JAR into the Syncope web application:

```bash
cp mysql-connector-j-9.6.0.jar /syncope/syncope-standalone-4.1.1-SNAPSHOT/apache-tomcat-10.1.54/webapps/syncope/WEB-INF/lib
```

## 5. ConnId Bundle Location

Set the ConnId bundle location in `core-embedded.properties`:

```properties
provisioning.connIdLocation=${syncope.connid.location},file:/syncope/syncope-original-mysql/syncope-standalone-4.1.1-SNAPSHOT/apache-tomcat-10.1.54/webapps/syncope-fit-build-tools/WEB-INF/classes/bundles/
```

Also update `bin/setenv.sh`:

```bash
-Dsyncope.connid.location=/syncope/syncope-original-mysql/syncope-standalone-4.1.1-SNAPSHOT/apache-tomcat-10.1.54/webapps/syncope-fit-build-tools/WEB-INF/classes/bundles/
```

## 6. Start Syncope

Grant execute permission if needed:

```bash
chmod 750 /syncope/syncope-standalone-4.1.1-SNAPSHOT/apache-tomcat-10.1.54/bin/catalina.sh
```

Start the service:

```bash
/syncope/syncope-standalone-4.1.1-SNAPSHOT/apache-tomcat-10.1.54/bin/catalina.sh start
```

## 7. Login and Administrator Account

Open the Syncope console:

```text
http://localhost:9080/syncope-console
```

Default credentials:

- **Username:** `admin`
- **Password:** `password`

![Syncope login page](docs-images/scim-to-gravitino/image16.png)

To change the administrator password, update `core.properties`:

```properties
security.adminUser=admin
security.adminPassword=DE088591C00CC98B36F5ADAAF7DA2B004CF7F2FE7BBB45B766B6409876E2F3DB13C7905C6AA59464
security.adminPasswordAlgorithm=SSHA256
```

## 8. Pull and Push Basics

Syncope supports two main task categories:

- **Pull tasks**
- **Push tasks**

### 8.1 Pull modes

| Mode | Purpose | Recommended use |
|---|---|---|
| `FULL_RECONCILIATION` | Scan all objects on the external resource | Initial import or full resynchronization |
| `FILTERED_RECONCILIATION` | Process only objects matching a filter | Partial synchronization |
| `INCREMENTAL` | Process only changes since the last sync token | Ongoing synchronization |

### 8.2 Push behavior

Push behavior is mainly controlled by:

- `matchingRule`
- `unmatchingRule`

Common matching rules:

- `IGNORE`
- `UPDATE`
- `DEPROVISION`
- `UNASSIGN`
- `UNLINK`
- `LINK`

Common unmatching rules:

- `IGNORE`
- `ASSIGN`
- `PROVISION`
- `UNLINK`

#### Matching rule reference

| Rule | Effect | Meaning |
|---|---|---|
| `IGNORE` | Do nothing | A match exists, but Syncope skips it |
| `UPDATE` | Update the matched object | Pull updates the Syncope object; Push updates the resource object |
| `DEPROVISION` | Delete the resource-side object | Removes the object from the external resource |
| `UNASSIGN` | Remove the assignment and delete the resource-side object | Unlinks it and removes it from the resource |
| `UNLINK` | Remove only the link, no provisioning or deprovisioning | Keeps the object and removes the association |
| `LINK` | Create only the link, no provisioning or deprovisioning | Keeps the object and adds the association |

#### Unmatching rule reference

| Rule | Effect | Meaning |
|---|---|---|
| `IGNORE` | Do nothing | No match found, so Syncope skips it |
| `ASSIGN` | Create the object and link it | Pull creates it in Syncope and links it; Push creates it on the resource and links it |
| `PROVISION` | Create the object without linking it | The object is created, but no link is established |
| `UNLINK` | Remove only the link, no provisioning or deprovisioning | Mainly meaningful for Push; in Pull / Sync code it is effectively the same as `IGNORE` |

#### Creation and link behavior summary

| Rule | Creates object | Creates link |
|---|---|---|
| `ASSIGN` | Yes | Yes |
| `PROVISION` | Yes | No |
| `LINK` | No | Yes |
| `UNLINK` | No | No, and removes any existing link |

## 9. Azure Connector for Pull

The Azure connector is used in **Pull mode** to import users and groups from Azure into Syncope.

### 9.1 Required Azure settings

- Client Id
- Authority: `https://login.microsoftonline.com/{tenantId}/`
- Redirect URI: `https://login.live.com/oauth20_desktop.srf`
- Resource URI: `https://graph.windows.net`
- Username
- Password
- Domain
- Tenant ID
- Client secret
- Scopes: `https://graph.microsoft.com/.default`

### 9.2 Azure prerequisites

By default, Azure connector queries can fail because of Azure permission or authentication settings.  
Before using the connector, update the Azure account and application configuration as follows.

#### Disable Per-user MFA

Use this if MFA was enabled through the legacy per-user MFA configuration.

URL:

`https://entra.microsoft.com/#view/Microsoft_AAD_AuthenticationMethods/MultifactorAuthenticationConfig.ReactView/tabId/users`

Steps:

1. Open the Microsoft Entra admin center.
2. Go to **Users**.
3. Open **Per-user MFA / Multi-Factor Authentication**.
4. Select the user.
5. Click **Disable**.

![Disable Per-user MFA](docs-images/scim-to-gravitino/word123-image27.png)

#### Disable Security Defaults

Use this if the tenant is enforcing MFA through Microsoft Security Defaults.

URL:

`https://entra.microsoft.com/#view/Microsoft_AAD_ConditionalAccess/PoliciesList.ReactView`

Steps:

1. Open the Microsoft Entra admin center.
2. Go to **Identity -> Overview -> Properties**.
3. Select **Manage security defaults**.
4. Set **Enable security defaults** to **No**.
5. Save.

![Disable Security Defaults](docs-images/scim-to-gravitino/word123-image5.png)

#### Allow public client flow

Steps:

1. Sign in to the Microsoft Entra admin center or Azure portal.
2. Go to **Applications -> App registrations** and select your application.
3. Under **Manage**, select **Authentication**.
4. Scroll to the **Advanced settings** section at the bottom.
5. Locate **Allow public client flows** and set it to **Yes**.
6. Save.

![Allow public client flows](docs-images/scim-to-gravitino/word123-image14.png)

#### Add Microsoft Graph API permissions

Steps:

1. Open the Microsoft Entra admin center: `https://entra.microsoft.com/`
2. Go to **Applications -> App registrations**.
3. Select your app.
4. Open **API permissions**.
5. Click **+ Add a permission**.
6. Choose the target API, such as **Microsoft Graph**.
7. Choose **Application permissions**.
8. Select the required permissions.
9. Click **Add permissions**.

Required permissions:

- `Group.Read.All`
- `User.Read.All`
- `User.Read`
- `GroupMember.Read.All`
- `Directory.Read.All`

![Add Microsoft Graph permissions](docs-images/scim-to-gravitino/word123-image17.png)

## 10. Import Azure Certificates into the Truststore

Because the Azure connector uses HTTPS endpoints, the relevant certificates must be imported into `keystore.jks`.

Recommended workflow:

1. Download the certificate chains for:
   - `graph.microsoft.com`
   - `login.microsoftonline.com`
   - optionally `graph.windows.net`
2. Download the DigiCert Global Root G2 certificate.
3. Back up the current keystore.
4. Import the certificates with `keytool`.
5. Restart Tomcat.

The imported aliases should include:

- `digicert-global-root-g2`
- `digicert-global-g2-tls-rsa-2020-ca1`

## 11. REST Connector for Push to Gravitino

The REST connector is used in **Push mode** to export data from Syncope to Gravitino.

### 11.1 Required REST connector settings

- Client Id
- Accept
- Content-Type
- scriptingLanguage
- createScriptFileName
- deleteScriptFileName
- searchScriptFileName

### 11.2 Typical capabilities

- `DELETE`
- `CREATE`
- `UPDATE`
- `SEARCH`

REST connector configuration example:

![REST connector configuration](docs-images/scim-to-gravitino/image35.png)

## 12. Syncope as a SCIM Service

Syncope also exposes a standard **SCIM** interface, so Azure, OneLogin, Okta, and similar systems can push data into Syncope.

### 12.1 Enterprise user configuration

For `POST /scim/v2/Users`, requests may include both the core user schema and the enterprise user schema.  
Therefore, `enterpriseUserConf` must not be empty.

![Enterprise user configuration](docs-images/scim-to-gravitino/image22.png)

### 12.2 Generate an access token

```bash
curl -sD - -o /dev/null -u admin:password -X POST http://localhost:9080/syncope/rest/accessTokens/token
```

### 12.3 Public deployment

Deploy Syncope to a reachable public network endpoint, then configure the SCIM URL and token on the third-party platform.

Example Azure SCIM configuration:

![Azure SCIM configuration](docs-images/scim-to-gravitino/image11.png)

### 12.4 Enable Detailed REST Logging

If you want to view the complete SCIM interface information (including input and output parameters, interface status, etc.).
Add the following to `core.properties`:

```properties
rest.logging.enabled=true
rest.logging.pretty=true
rest.logging.verbose=true
rest.logging.limit=2147483647
rest.logging.log-binary=false
rest.logging.log-multipart=true
```

Then update `log4j2.xml`:

```xml
<asyncLogger name="org.apache.cxf" additivity="false" level="INFO">
  <appender-ref ref="rest"/>
</asyncLogger>
```

This is useful for SCIM and REST request troubleshooting.

## 13. Production environment case

### 13.1 Real-time user and group push to Gravitino

1. Create a new REST connector.
2. Configure the Gravitino endpoint and scripts.
3. Enable the required capabilities.
4. Create a resource under the connector.
5. Create a push task.
6. Configure provisioning rules for both `USER` and `GROUP`.

Reference screenshots:

![Create REST connector from topology](docs-images/scim-to-gravitino/image38.png)
![Configure REST connector endpoint and scripts](docs-images/scim-to-gravitino/image18.png)
![Configure REST connector details](docs-images/scim-to-gravitino/image9.png)
![Select connector capabilities](docs-images/scim-to-gravitino/image17.png)
![Create resource under REST connector](docs-images/scim-to-gravitino/image32.png)
![Create push task](docs-images/scim-to-gravitino/image33.png)
![Push task configuration](docs-images/scim-to-gravitino/image6.png)
![Open provision rules](docs-images/scim-to-gravitino/image8.png)
![Configure USER and GROUP mappings](docs-images/scim-to-gravitino/image39.png)

For `USER.username`, use this template:

```groovy
value.replaceAll('([^@]+)@.*', '$1')
```

![USER username template example](docs-images/scim-to-gravitino/image34.png)
![Template configuration example](docs-images/scim-to-gravitino/image14.png)
![Template editor view](docs-images/scim-to-gravitino/image36.png)

Also configure Realm templates for both `USER` and `GROUP`.

![Realm template entry point](docs-images/scim-to-gravitino/image10.png)
![Realm template assignment](docs-images/scim-to-gravitino/image30.png)
![External resource selection](docs-images/scim-to-gravitino/image31.png)

### 13.2 Third-party platform push to Syncope

Configure Azure / OneLogin / Okta to push user and group data into Syncope through the SCIM endpoint.

Typical behavior:

- first run: full push
- later runs: incremental push every 20 to 40 minutes

![Azure SCIM configuration example](docs-images/scim-to-gravitino/image11.png)

## 14. Data Recovery Procedure

If an emergency occurs, suspend third-party provisioning first, then run the recovery pull and push tasks from Syncope.

### 14.1 Pause provisioning in Microsoft Entra

1. Open Microsoft Entra admin center.
2. Go to **Identity > Applications > Enterprise applications**.
3. Select the application.
4. Open **Provisioning**.
5. Set **Provisioning Status** to **Off**.
6. Save.

![Pause provisioning in Microsoft Entra](docs-images/scim-to-gravitino/image25.png)
![Pause provisioning in Microsoft Entra example](docs-images/scim-to-gravitino/image30.png)

### 14.2 Register the orphan-cleanup inbound action

```bash
curl -k -u admin:password \
  -H 'Content-Type: application/json' \
  -X POST \
  https://localhost:9443/syncope/rest/implementations/INBOUND_ACTIONS/OrphanCleanupInboundActions \
  -d '{
    "key": "OrphanCleanupInboundActions",
    "engine": "JAVA",
    "type": "INBOUND_ACTIONS",
    "body": "org.apache.syncope.core.provisioning.java.pushpull.OrphanCleanupInboundActions"
  }'
```

### 14.3 Configure the recovery pull task

Create a dedicated Azure connector for recovery, then create a resource under that connector and open **Provision Rules**.

![Create Azure connector for recovery](docs-images/scim-to-gravitino/image9.png)
![Recovery connector basic settings](docs-images/scim-to-gravitino/image25.png)
![Recovery connector additional settings](docs-images/scim-to-gravitino/image8.png)
![Recovery connector final settings](docs-images/scim-to-gravitino/image16.png)

For both `USER` and `GROUP`, configure the recovery mappings and templates.  
Add a filtered reconciliation rule so that only objects for the target Azure application are included. Replace the following Azure client ID with your own:

```groovy
import java.util.LinkedHashSet
import org.identityconnectors.framework.common.objects.AttributeBuilder
import org.identityconnectors.framework.common.objects.ObjectClass
import org.identityconnectors.framework.common.objects.OperationOptions
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder
import org.identityconnectors.framework.common.objects.filter.Filter
import org.identityconnectors.framework.common.objects.filter.FilterBuilder
import org.apache.syncope.core.provisioning.api.pushpull.ReconFilterBuilder

class AppIdReconFilterBuilder implements ReconFilterBuilder {
  private static final String APP_ID = "replace-with-your-azure-client-id"

  @Override
  Filter build(final ObjectClass objectClass) {
    FilterBuilder.equalTo(AttributeBuilder.build("appId", APP_ID))
  }

  @Override
  OperationOptions build(final ObjectClass objectClass, final OperationOptions initialOptions) {
    def attrsToGet = new LinkedHashSet<String>()
    if (initialOptions?.getAttributesToGet() != null) {
      attrsToGet.addAll(initialOptions.getAttributesToGet())
    }
    attrsToGet.add("appId")
    new OperationOptionsBuilder(initialOptions).setAttributesToGet(attrsToGet as String[]).build()
  }
}
```

Then create a new pull task and configure templates for both `USER` and `GROUP`.

Recovery workflow screenshots:

![Create resource and open provision rules](docs-images/scim-to-gravitino/image21.png)
![Recovery USER and GROUP mapping configuration](docs-images/scim-to-gravitino/image3.png)
![Recovery USER mapping example](docs-images/scim-to-gravitino/image29.png)
![Recovery GROUP mapping example](docs-images/scim-to-gravitino/image44.png)
![Recovery mapping details](docs-images/scim-to-gravitino/image34.png)
![Recovery filter builder configuration](docs-images/scim-to-gravitino/image19.png)
![Create pull task](docs-images/scim-to-gravitino/image32.png)
![Pull task configuration](docs-images/scim-to-gravitino/image18.png)
![Open pull task template configuration](docs-images/scim-to-gravitino/image20.png)
![Configure USER template](docs-images/scim-to-gravitino/image23.png)
![Configure GROUP template](docs-images/scim-to-gravitino/image26.png)

This recovery pull setup should include:

- a new Azure connector
- a new resource
- provisioning rules for `USER` and `GROUP`
- a new pull task
- templates for both `USER` and `GROUP`

### 14.4 Configure the recovery push task

Copy the existing Gravitino resource used for push, then change `searchScriptFileName` to `ListSearchScript.groovy`.

![Copy the existing Gravitino resource](docs-images/scim-to-gravitino/image43.png)
![Update searchScriptFileName to ListSearchScript.groovy](docs-images/scim-to-gravitino/image39.png)

After that, create the push task that will republish the recovered data from Syncope to Gravitino.

![Create the recovery push task](docs-images/scim-to-gravitino/image41.png)
![Recovery push task configuration](docs-images/scim-to-gravitino/image37.png)

### 14.5 Recovery execution order

1. Stop the task pushed by the third-party platform.
2. Execute the Azure full-data pull task in Syncope. This step also clears orphan objects.
3. Execute the Syncope push task to Gravitino. This step also clears orphan objects on the target side.
4. Re-enable the task pushed by the third-party platform.

## 15. Email Service

Run Mailpit:

```bash
docker run -d --name mailpit -p 2525:1025 -p 8025:8025 axllent/mailpit
```

- **SMTP:** `localhost:2525`
- **Web UI:** `http://localhost:8025`

Common mail-related settings:

- `spring.mail.host`
- `spring.mail.port`
- `spring.mail.properties.mail.smtp.auth`
- `spring.mail.properties.mail.smtp.starttls.enable`

Reference:

`https://www.tirasa.net/en/blog/apache-syncope-notification-e-mails`

## 16. Summary

With this setup, Syncope can:

- run on MySQL
- pull users and groups from Azure
- expose SCIM endpoints for third-party provisioning
- push synchronized data to Gravitino through the REST connector
- support orphan cleanup and recovery workflows
- provide detailed REST logging and email notification support
