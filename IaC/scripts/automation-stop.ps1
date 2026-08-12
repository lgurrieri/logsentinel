<#
Runbook de apagado de la VM de demo de LogSentinel (Azure Automation, identidad
administrada de sistema con rol "Virtual Machine Contributor" acotado a la VM --
ver provision-vm.sh). Programado para correr a las 16:15 ART.

`-Deallocate` es la parte que importa: libera el cómputo (deja de facturarse por
hora de VM) y no solo apaga el SO -- una VM "stopped" pero no deallocated sigue
cobrando.
#>

$ErrorActionPreference = 'Stop'

Connect-AzAccount -Identity | Out-Null

$resourceGroup = Get-AutomationVariable -Name 'ResourceGroupName'
$vmName        = Get-AutomationVariable -Name 'VMName'

Write-Output "==> Apagando (deallocate) $vmName en $resourceGroup"
Stop-AzVM -ResourceGroupName $resourceGroup -Name $vmName -Force | Out-Null

Write-Output "==> VM deallocated. Cómputo dejó de facturarse (disco + IP Standard siguen activos)."
