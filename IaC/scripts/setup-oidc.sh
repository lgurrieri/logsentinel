#!/usr/bin/env bash
# Aprovisiona la identidad OIDC federada que usa el job `deploy` de
# `.github/workflows/cd.yml` para autenticar contra Azure sin secretos de
# larga vida (ver DEBT-009 en docs/deuda-tecnica.md). Separado de
# provision-vm.sh porque el App Registration vive a nivel de tenant de Azure
# AD, no del resource group de la VM.
#
# Requiere: `az login` ya hecho a mano, la VM y el Storage Account ya creados
# por provision-vm.sh, y las siguientes variables de entorno exportadas:
#   RESOURCE_GROUP, VM_NAME, GITHUB_REPO (ej. lgurrieri/logsentinel),
#   GITHUB_ENVIRONMENT (ej. production)
#
# Idempotente: reusa el App Registration/Service Principal si ya existen
# (detectados por --display-name), y `az role assignment create` es seguro
# de re-ejecutar (Azure no duplica asignaciones idénticas).
set -euo pipefail

: "${RESOURCE_GROUP:?falta RESOURCE_GROUP}"
: "${VM_NAME:?falta VM_NAME}"
: "${GITHUB_REPO:?falta GITHUB_REPO (ej. lgurrieri/logsentinel)}"
: "${GITHUB_ENVIRONMENT:?falta GITHUB_ENVIRONMENT (ej. production)}"

APP_NAME="${APP_NAME:-logsentinel-cd-oidc}"
STORAGE_ACCOUNT_NAME="${STORAGE_ACCOUNT_NAME:-logsentineldeploy}"

echo "==> Confirmando suscripción activa"
az account show --query "{name:name, id:id, tenantId:tenantId}" -o table

# ---------------------------------------------------------------------------
# App Registration + Service Principal (idempotente por display-name)
# ---------------------------------------------------------------------------
echo "==> App Registration: ${APP_NAME}"
APP_ID="$(az ad app list --display-name "${APP_NAME}" --query "[0].appId" -o tsv)"
if [ -z "${APP_ID}" ]; then
  APP_ID="$(az ad app create --display-name "${APP_NAME}" --query appId -o tsv)"
  echo "==> Creado App Registration, appId=${APP_ID}"
else
  echo "==> Ya existía, appId=${APP_ID}"
fi

SP_ID="$(az ad sp list --filter "appId eq '${APP_ID}'" --query "[0].id" -o tsv)"
if [ -z "${SP_ID}" ]; then
  SP_ID="$(az ad sp create --id "${APP_ID}" --query id -o tsv)"
  echo "==> Creado Service Principal, id=${SP_ID}"
else
  echo "==> Ya existía Service Principal, id=${SP_ID}"
fi

# ---------------------------------------------------------------------------
# Federated Identity Credential -- token de corta vida emitido por GitHub
# Actions, sin client-secret persistido en ningún lado.
#
# GitHub emite el claim `sub` calificado con los IDs numéricos e inmutables
# del owner/repo (`repo:OWNER@OWNER_ID/REPO@REPO_ID:environment:...`), no solo
# los nombres -- así el subject sigue siendo válido si el owner o el repo se
# renombran más adelante. Hay que usar ese mismo formato acá o Azure AD
# rechaza el token con AADSTS700213 (verificado en este deploy).
# ---------------------------------------------------------------------------
echo "==> Resolviendo IDs inmutables de ${GITHUB_REPO}"
GITHUB_OWNER="${GITHUB_REPO%%/*}"
GITHUB_REPO_NAME="${GITHUB_REPO##*/}"
OWNER_ID="$(gh api "users/${GITHUB_OWNER}" --jq '.id')"
REPO_ID="$(gh api "repos/${GITHUB_REPO}" --jq '.id')"

echo "==> Federated Identity Credential para ${GITHUB_REPO}:environment:${GITHUB_ENVIRONMENT}"
FEDERATED_CRED_FILE="$(mktemp)"
trap 'rm -f "${FEDERATED_CRED_FILE}"' EXIT
cat > "${FEDERATED_CRED_FILE}" <<EOF
{
  "name": "logsentinel-cd-${GITHUB_ENVIRONMENT}",
  "issuer": "https://token.actions.githubusercontent.com",
  "subject": "repo:${GITHUB_OWNER}@${OWNER_ID}/${GITHUB_REPO_NAME}@${REPO_ID}:environment:${GITHUB_ENVIRONMENT}",
  "audiences": ["api://AzureADTokenExchange"]
}
EOF

if az ad app federated-credential show --id "${APP_ID}" --federated-credential-id "logsentinel-cd-${GITHUB_ENVIRONMENT}" >/dev/null 2>&1; then
  az ad app federated-credential update --id "${APP_ID}" --federated-credential-id "logsentinel-cd-${GITHUB_ENVIRONMENT}" --parameters "${FEDERATED_CRED_FILE}" -o none
  echo "==> Federated credential actualizada (subject re-verificado)"
else
  az ad app federated-credential create --id "${APP_ID}" --parameters "${FEDERATED_CRED_FILE}" -o none
  echo "==> Federated credential creada"
fi

# ---------------------------------------------------------------------------
# RBAC acotado por recurso -- mismo patrón que la Automation Account en
# provision-vm.sh: mínimo privilegio, scope al recurso puntual, no al RG.
# ---------------------------------------------------------------------------
VM_ID="$(az vm show --resource-group "${RESOURCE_GROUP}" --name "${VM_NAME}" --query id -o tsv)"
STORAGE_ID="$(az storage account show --resource-group "${RESOURCE_GROUP}" --name "${STORAGE_ACCOUNT_NAME}" --query id -o tsv)"

echo "==> Rol 'Virtual Machine Contributor' acotado a la VM"
az role assignment create \
  --assignee-object-id "${SP_ID}" --assignee-principal-type ServicePrincipal \
  --role "Virtual Machine Contributor" \
  --scope "${VM_ID}" -o none 2>/dev/null || echo "    (ya existía)"

echo "==> Rol 'Storage Blob Data Contributor' acotado al Storage Account"
az role assignment create \
  --assignee-object-id "${SP_ID}" --assignee-principal-type ServicePrincipal \
  --role "Storage Blob Data Contributor" \
  --scope "${STORAGE_ID}" -o none 2>/dev/null || echo "    (ya existía)"

TENANT_ID="$(az account show --query tenantId -o tsv)"
SUBSCRIPTION_ID="$(az account show --query id -o tsv)"

echo ""
echo "==> Listo. Configurar estas GitHub Environment variables en ${GITHUB_REPO} (environment: ${GITHUB_ENVIRONMENT}):"
echo "    AZURE_CLIENT_ID=${APP_ID}"
echo "    AZURE_TENANT_ID=${TENANT_ID}"
echo "    AZURE_SUBSCRIPTION_ID=${SUBSCRIPTION_ID}"
echo "    AZURE_STORAGE_ACCOUNT=${STORAGE_ACCOUNT_NAME}"
echo "    (gh variable set NOMBRE --env ${GITHUB_ENVIRONMENT} --body VALOR)"
