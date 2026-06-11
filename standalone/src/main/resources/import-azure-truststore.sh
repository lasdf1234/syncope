#!/usr/bin/env bash
#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#
# Import Azure / Microsoft Entra HTTPS trust anchors into Syncope Tomcat keystore.
#
# Syncope setenv.sh uses:
#   -Djavax.net.ssl.trustStore=$CATALINA_HOME/conf/keystore.jks
# Azure ConnId connector (MSAL) needs these CAs/end-entity certs for token calls to:
#   login.microsoftonline.com, graph.microsoft.com, graph.windows.net
#
# Usage:
#   ./import-azure-truststore.sh [CATALINA_HOME]
#
# Environment overrides:
#   KEYSTORE       path to JKS (default: $CATALINA_HOME/conf/keystore.jks)
#   STORE_PASS     keystore password (default: password)
#   TMPDIR         temp directory for downloaded PEM files

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [[ $# -ge 1 ]]; then
  CATALINA_HOME="$1"
elif [[ -n "${CATALINA_HOME:-}" ]]; then
  :
elif [[ -d "${SCRIPT_DIR}/../conf" ]]; then
  CATALINA_HOME="$(cd "${SCRIPT_DIR}/.." && pwd)"
else
  echo "ERROR: set CATALINA_HOME or pass it as the first argument." >&2
  echo "Example: $0 /path/to/apache-tomcat-10.1.54" >&2
  exit 1
fi

KEYSTORE="${KEYSTORE:-${CATALINA_HOME}/conf/keystore.jks}"
STORE_PASS="${STORE_PASS:-password}"
WORKDIR="${TMPDIR:-/tmp}/syncope-azure-certs-$$"

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "ERROR: required command not found: $1" >&2
    exit 1
  fi
}

require_cmd curl
require_cmd openssl
require_cmd keytool

if [[ ! -f "${KEYSTORE}" ]]; then
  echo "ERROR: keystore not found: ${KEYSTORE}" >&2
  exit 1
fi

mkdir -p "${WORKDIR}"
trap 'rm -rf "${WORKDIR}"' EXIT

backup_keystore() {
  local backup="${KEYSTORE}.bak.$(date +%Y%m%d%H%M%S)"
  cp "${KEYSTORE}" "${backup}"
  echo "Backup: ${backup}"
}

alias_exists() {
  keytool -list -alias "$1" -keystore "${KEYSTORE}" -storepass "${STORE_PASS}" >/dev/null 2>&1
}

import_pem() {
  local alias="$1"
  local pem_file="$2"

  if [[ ! -s "${pem_file}" ]]; then
    echo "ERROR: certificate file is empty: ${pem_file}" >&2
    return 1
  fi

  if alias_exists "${alias}"; then
    echo "Replacing existing alias: ${alias}"
    keytool -delete -alias "${alias}" -keystore "${KEYSTORE}" -storepass "${STORE_PASS}" >/dev/null 2>&1 || true
  fi

  keytool -importcert -noprompt \
    -alias "${alias}" \
    -file "${pem_file}" \
    -keystore "${KEYSTORE}" \
    -storepass "${STORE_PASS}"
  echo "Imported: ${alias}"
}

fetch_server_cert() {
  local host="$1"
  local outfile="$2"
  echo | openssl s_client -connect "${host}:443" -servername "${host}" 2>/dev/null \
    | openssl x509 -outform PEM > "${outfile}"
}

echo "CATALINA_HOME: ${CATALINA_HOME}"
echo "KEYSTORE:      ${KEYSTORE}"

backup_keystore

echo "Downloading DigiCert Global Root G2..."
curl -fsSL -o "${WORKDIR}/DigiCertGlobalRootG2.crt.pem" \
  https://cacerts.digicert.com/DigiCertGlobalRootG2.crt.pem
import_pem "digicert-global-root-g2" "${WORKDIR}/DigiCertGlobalRootG2.crt.pem"

HOSTS=(
  "login.microsoftonline.com"
  "graph.microsoft.com"
  "graph.windows.net"
)

for host in "${HOSTS[@]}"; do
  alias_name="${host//./-}"
  echo "Fetching server certificate: ${host}..."
  fetch_server_cert "${host}" "${WORKDIR}/${alias_name}.pem"
  import_pem "${alias_name}" "${WORKDIR}/${alias_name}.pem"
done

echo
echo "Done. Truststore entries:"
keytool -list -keystore "${KEYSTORE}" -storepass "${STORE_PASS}" | grep -E '^(digicert|login|graph|Keystore|密钥库|Your keystore|您的密钥库)' || \
  keytool -list -keystore "${KEYSTORE}" -storepass "${STORE_PASS}"

echo
echo "Restart Tomcat so the Azure connector picks up the updated truststore:"
echo "  \"${CATALINA_HOME}/bin/shutdown.sh\" && \"${CATALINA_HOME}/bin/startup.sh\""
