<#
Runbook de arranque de la VM de demo de LogSentinel (Azure Automation, identidad
administrada de sistema con rol "Virtual Machine Contributor" acotado a la VM --
ver provision-vm.sh). Programado para correr a las 10:30 ART.

Enciende la VM y corre un smoke test real DENTRO de ella (via run-command) antes
de reportar éxito: espera que docker compose reporte los servicios healthy, pega
un curl a /actuator/health y crea un incidente de prueba -- así un arranque roto
se descubre acá y no recién frente a la audiencia.

Nota: backend/db/ollama nunca publican puertos al host (ver docker-compose.prod.yml,
`ports: !reset []`) -- el único punto verificable desde afuera del contenedor es
nginx en :80, por eso el smoke test corre adentro de la VM (localhost) en vez de
contra la IP pública.

Assets esperados en la Automation Account:
  - Variables:  ResourceGroupName, VMName
  - Credential: NginxBasicAuth (usuario/password del Basic Auth público de nginx)
#>

$ErrorActionPreference = 'Stop'

Connect-AzAccount -Identity | Out-Null

$resourceGroup = Get-AutomationVariable -Name 'ResourceGroupName'
$vmName        = Get-AutomationVariable -Name 'VMName'
$nginxCred     = Get-AutomationPSCredential -Name 'NginxBasicAuth'
$nginxUser     = $nginxCred.UserName
$nginxPass     = $nginxCred.GetNetworkCredential().Password

Write-Output "==> Encendiendo $vmName en $resourceGroup"
Start-AzVM -ResourceGroupName $resourceGroup -Name $vmName | Out-Null

$smokeScriptTemplate = @'
set -euo pipefail
cd /opt/logsentinel

echo "Esperando que docker compose reporte todos los servicios healthy..."
ok=0
for i in $(seq 1 30); do
  unhealthy=$(docker compose -f docker-compose.yml -f docker-compose.prod.yml ps --format json \
    | python3 -c "import sys,json; rows=[json.loads(l) for l in sys.stdin if l.strip()]; print(sum(1 for r in rows if r.get('Health') not in ('healthy','')))")
  if [ "$unhealthy" -eq 0 ]; then
    ok=1
    break
  fi
  sleep 10
done
if [ "$ok" -ne 1 ]; then
  echo "FALLO: no todos los servicios llegaron a healthy tras 5 minutos" >&2
  exit 1
fi
echo "Todos los servicios healthy."

echo "Chequeando /actuator/health via nginx..."
code=$(curl -s -o /dev/null -w '%{http_code}' -u "__NGINX_USER__:__NGINX_PASS__" http://localhost/actuator/health)
if [ "$code" != "200" ]; then
  echo "FALLO: /actuator/health devolvio $code" >&2
  exit 1
fi

echo "Creando incidente de prueba (smoke test funcional end-to-end)..."
code=$(curl -s -o /dev/null -w '%{http_code}' -u "__NGINX_USER__:__NGINX_PASS__" \
  -X POST http://localhost/api/v1/incidents \
  -H 'Content-Type: application/json' \
  -d '{"systemName":"auth-service","urgency":"LOW","rawLogSnapshot":"smoke test automation-start.ps1"}')
if [ "$code" != "201" ]; then
  echo "FALLO: creacion de incidente de prueba devolvio $code (esperado 201)" >&2
  exit 1
fi

echo "Smoke test OK."
'@

$smokeScript = $smokeScriptTemplate.Replace('__NGINX_USER__', $nginxUser).Replace('__NGINX_PASS__', $nginxPass)

Write-Output "==> Corriendo smoke test dentro de la VM (puede tardar unos minutos)"
$result = Invoke-AzVMRunCommand -ResourceGroupName $resourceGroup -VMName $vmName `
  -CommandId 'RunShellScript' -ScriptString $smokeScript

$output = ($result.Value | Where-Object { $_.Code -eq 'ComponentStatus/StdOut/succeeded' }).Message
$errorOutput = ($result.Value | Where-Object { $_.Code -eq 'ComponentStatus/StdErr/succeeded' }).Message

Write-Output $output
if ($errorOutput) { Write-Output $errorOutput }

if ($output -notmatch 'Smoke test OK\.' -or $errorOutput -match 'FALLO') {
  throw "Smoke test fallo -- ver output arriba. La VM queda encendida para debug manual (no se apaga sola)."
}

Write-Output "==> VM encendida y smoke test OK. Lista para la demo."
