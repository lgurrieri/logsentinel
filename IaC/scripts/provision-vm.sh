#!/usr/bin/env bash
# Aprovisiona la VM de demo de LogSentinel en Azure para la ventana de hoy
# (10:30-16:15 ART). Imperativo (az CLI), no Bicep -- ver DEBT-008.
#
# Requiere: `az login` ya hecho a mano (sesión interactiva, ver docs/demo-runbook.md
# seccion "Despliegue en Azure"), y las siguientes variables de entorno exportadas
# antes de correr este script:
#   RESOURCE_GROUP, LOCATION, VM_NAME, DNS_LABEL, ADMIN_USER,
#   SSH_PUBLIC_KEY_PATH, POSTGRES_PASSWORD, NGINX_BASIC_AUTH_USER,
#   NGINX_BASIC_AUTH_PASS, MY_PUBLIC_IP, BUDGET_AMOUNT_ARS, BUDGET_ALERT_EMAIL
#
# Idempotente: cada `az ... create` es seguro de re-ejecutar (Azure actualiza el
# recurso existente en vez de duplicarlo cuando el nombre coincide).
set -euo pipefail

: "${RESOURCE_GROUP:?falta RESOURCE_GROUP}"
: "${LOCATION:?falta LOCATION}"
: "${VM_NAME:?falta VM_NAME}"
: "${DNS_LABEL:?falta DNS_LABEL}"
: "${ADMIN_USER:?falta ADMIN_USER}"
: "${SSH_PUBLIC_KEY_PATH:?falta SSH_PUBLIC_KEY_PATH}"
: "${POSTGRES_PASSWORD:?falta POSTGRES_PASSWORD}"
: "${NGINX_BASIC_AUTH_USER:?falta NGINX_BASIC_AUTH_USER}"
: "${NGINX_BASIC_AUTH_PASS:?falta NGINX_BASIC_AUTH_PASS}"
: "${MY_PUBLIC_IP:?falta MY_PUBLIC_IP (ver: curl -s ifconfig.me)}"
: "${BUDGET_AMOUNT_ARS:?falta BUDGET_AMOUNT_ARS}"
: "${BUDGET_ALERT_EMAIL:?falta BUDGET_ALERT_EMAIL}"

VM_SIZE="${VM_SIZE:-Standard_D4as_v4}"
NSG_NAME="${VM_NAME}-nsg"
VNET_NAME="${VM_NAME}-vnet"
SUBNET_NAME="${VM_NAME}-subnet"
PIP_NAME="${VM_NAME}-pip"
NIC_NAME="${VM_NAME}-nic"
AUTOMATION_ACCOUNT_NAME="${VM_NAME}-automation"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLOUD_INIT_TEMPLATE="${SCRIPT_DIR}/cloud-init.yaml"
CLOUD_INIT_RENDERED="$(mktemp)"
trap 'rm -f "${CLOUD_INIT_RENDERED}"' EXIT

echo "==> Confirmando suscripción activa"
az account show --query "{name:name, id:id, tenantId:tenantId}" -o table

echo "==> Resource group: ${RESOURCE_GROUP} (${LOCATION})"
az group create --name "${RESOURCE_GROUP}" --location "${LOCATION}" -o none

# ---------------------------------------------------------------------------
# Budget + alerta -- PRIMERO, antes de crear ningún recurso facturable.
# Red de seguridad ante un fallo silencioso del runbook de apagado.
# ---------------------------------------------------------------------------
echo "==> Budget de ARS ${BUDGET_AMOUNT_ARS}/mes con alerta al 80% y 100%"
RG_ID="$(az group show --name "${RESOURCE_GROUP}" --query id -o tsv)"
az consumption budget create \
  --budget-name "${VM_NAME}-budget" \
  --category cost \
  --amount "${BUDGET_AMOUNT_ARS}" \
  --time-grain monthly \
  --start-date "$(date -u +%Y-%m-01)" \
  --end-date "$(date -u -d '+1 year' +%Y-%m-01 2>/dev/null || date -u -v+1y +%Y-%m-01)" \
  --resource-group "${RESOURCE_GROUP}" \
  --notifications '{
    "Alert80": {"enabled": true, "operator": "GreaterThanOrEqualTo", "threshold": 80, "contactEmails": ["'"${BUDGET_ALERT_EMAIL}"'"]},
    "Alert100": {"enabled": true, "operator": "GreaterThanOrEqualTo", "threshold": 100, "contactEmails": ["'"${BUDGET_ALERT_EMAIL}"'"]}
  }' -o none || echo "AVISO: budget create falló o ya existe -- verificar manualmente en el portal antes de continuar."

# ---------------------------------------------------------------------------
# Red: VNet/Subnet + NSG (SSH solo desde MY_PUBLIC_IP, HTTP 80 público, resto denegado)
# ---------------------------------------------------------------------------
echo "==> Red: VNet/Subnet + NSG"
az network vnet create \
  --resource-group "${RESOURCE_GROUP}" \
  --name "${VNET_NAME}" \
  --subnet-name "${SUBNET_NAME}" -o none

az network nsg create --resource-group "${RESOURCE_GROUP}" --name "${NSG_NAME}" -o none

az network nsg rule create \
  --resource-group "${RESOURCE_GROUP}" --nsg-name "${NSG_NAME}" \
  --name AllowSSHFromMyIP --priority 100 \
  --source-address-prefixes "${MY_PUBLIC_IP}/32" --source-port-ranges '*' \
  --destination-port-ranges 22 --access Allow --protocol Tcp -o none

az network nsg rule create \
  --resource-group "${RESOURCE_GROUP}" --nsg-name "${NSG_NAME}" \
  --name AllowHTTP --priority 110 \
  --source-address-prefixes '*' --source-port-ranges '*' \
  --destination-port-ranges 80 --access Allow --protocol Tcp -o none

# Deniega explícitamente cualquier otro inbound (8080/5432/11434 nunca públicos --
# solo alcanzables dentro de la red interna de Docker en la VM).
az network nsg rule create \
  --resource-group "${RESOURCE_GROUP}" --nsg-name "${NSG_NAME}" \
  --name DenyAllOtherInbound --priority 4096 \
  --source-address-prefixes '*' --source-port-ranges '*' \
  --destination-port-ranges '*' --access Deny --protocol '*' -o none

# ---------------------------------------------------------------------------
# IP pública Standard (Basic SKU retirado) + DNS label estático
# ---------------------------------------------------------------------------
echo "==> IP pública Standard SKU + DNS label: ${DNS_LABEL}"
az network public-ip create \
  --resource-group "${RESOURCE_GROUP}" --name "${PIP_NAME}" \
  --sku Standard --allocation-method Static \
  --dns-name "${DNS_LABEL}" -o none

az network nic create \
  --resource-group "${RESOURCE_GROUP}" --name "${NIC_NAME}" \
  --vnet-name "${VNET_NAME}" --subnet "${SUBNET_NAME}" \
  --network-security-group "${NSG_NAME}" \
  --public-ip-address "${PIP_NAME}" -o none

# ---------------------------------------------------------------------------
# Render de cloud-init: inyecta secretos/params sin tocar el template versionado
# ---------------------------------------------------------------------------
echo "==> Renderizando cloud-init"
sed \
  -e "s|__POSTGRES_PASSWORD__|${POSTGRES_PASSWORD}|g" \
  -e "s|__NGINX_BASIC_AUTH_USER__|${NGINX_BASIC_AUTH_USER}|g" \
  -e "s|__NGINX_BASIC_AUTH_PASS__|${NGINX_BASIC_AUTH_PASS}|g" \
  "${CLOUD_INIT_TEMPLATE}" > "${CLOUD_INIT_RENDERED}"

# ---------------------------------------------------------------------------
# VM
# ---------------------------------------------------------------------------
echo "==> Creando VM: ${VM_NAME} (${VM_SIZE})"
az vm create \
  --resource-group "${RESOURCE_GROUP}" --name "${VM_NAME}" \
  --nics "${NIC_NAME}" \
  --image "Ubuntu2404" \
  --size "${VM_SIZE}" \
  --admin-username "${ADMIN_USER}" \
  --ssh-key-values "${SSH_PUBLIC_KEY_PATH}" \
  --custom-data "${CLOUD_INIT_RENDERED}" \
  --os-disk-size-gb 64 \
  -o table

echo "==> VM lista. FQDN público: ${DNS_LABEL}.${LOCATION}.cloudapp.azure.com"
echo "==> Esperar unos minutos a que cloud-init termine (docker compose up --wait)."
echo "==> Verificar con: az vm run-command invoke --resource-group ${RESOURCE_GROUP} --name ${VM_NAME} --command-id RunShellScript --scripts 'cloud-init status --wait'"

# ---------------------------------------------------------------------------
# Automation Account (para los runbooks de start/stop) con rol acotado
# ---------------------------------------------------------------------------
echo "==> Automation Account: ${AUTOMATION_ACCOUNT_NAME}"
az automation account create \
  --resource-group "${RESOURCE_GROUP}" --name "${AUTOMATION_ACCOUNT_NAME}" \
  --location "${LOCATION}" -o none

az automation account update \
  --resource-group "${RESOURCE_GROUP}" --name "${AUTOMATION_ACCOUNT_NAME}" \
  --assign-identity '[system]' -o none

PRINCIPAL_ID="$(az automation account show \
  --resource-group "${RESOURCE_GROUP}" --name "${AUTOMATION_ACCOUNT_NAME}" \
  --query identity.principalId -o tsv)"

VM_ID="$(az vm show --resource-group "${RESOURCE_GROUP}" --name "${VM_NAME}" --query id -o tsv)"

az role assignment create \
  --assignee-object-id "${PRINCIPAL_ID}" --assignee-principal-type ServicePrincipal \
  --role "Virtual Machine Contributor" \
  --scope "${VM_ID}" -o none

echo "==> Rol 'Virtual Machine Contributor' asignado, acotado únicamente a esta VM (no a todo el resource group)."
echo "==> Siguiente paso manual: subir los runbooks automation-start.ps1 / automation-stop.ps1"
echo "    y programar el schedule 10:30-16:15 ART (ver docs/demo-runbook.md)."
