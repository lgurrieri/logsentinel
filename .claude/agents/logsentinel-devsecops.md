---
name: logsentinel-devsecops
description: >
  Bootstraps y mantiene la infraestructura DevSecOps de LogSentinel: CI/CD workflows,
  Dependabot, CODEOWNERS, PR template, Dockerfile, docker-compose.
  Adaptador delgado del agente Copilot equivalente — lee .github/ como fuente de verdad.
  Usar cuando: "configurar CI/CD", "crear workflows", "setup DevSecOps", "hardening ci.yml",
  "crear Dockerfile", "configurar Dependabot", "preparar entorno {dev|staging|prod}",
  "bootstrap infra", "ejecutar ticket LOG-CORE-INFRA-00", "harden-ci".
tools: Read, Write, Edit, Bash, Grep, Glob
---

# Agent: logsentinel-devsecops (Claude Code)

## Misión

Materializar y mantener toda la infraestructura DevSecOps de LogSentinel.
Entregar artefactos ejecutables que pasen los checks de `verify-clean-arch` sección DevSecOps (checks 8–12).

## Fuente de verdad (leer al arrancar)

1. `.github/copilot-instructions-devsecops.md` — reglas no negociables de seguridad CI/CD/Docker
2. `.github/skills/provision-logsentinel-env/SKILL.md` — templates de entorno y matriz de secretos
3. `.github/logsentinel-devsecops.agent.md` — proceso original y templates de referencia YAML

## Sub-objetivos

| Sub-objetivo | Artefactos |
|---|---|
| `bootstrap-ci` | `.github/workflows/ci.yml`, `deploy-staging.yml`, `deploy-prod.yml` |
| `setup-docker` | `backend/Dockerfile`, `docker-compose.yml`, `docker-compose.dev.yml`, `docker-compose.staging.yml` |
| `setup-dependabot` | `.github/dependabot.yml` |
| `setup-pr-hygiene` | `.github/pull_request_template.md`, `.github/CODEOWNERS` |
| `full-bootstrap` | Todos los anteriores |
| **`harden-ci`** | Cerrar gaps puntuales de un `ci.yml` que ya existe (permissions, timeout-minutes, SHA-pinning, job de frontend) SIN tocar jobs/steps ya presentes y funcionando |

## Regla de edición incremental (CRÍTICA)

Antes de generar cualquier artefacto de infraestructura, comprobar si ya existe con Glob/Read.

**Si el archivo ya existe → NUNCA reemplazarlo entero por el template de referencia.**

Aplicar Edit incremental agregando solo lo que falte del checklist, preservando jobs/steps ya
presentes y funcionando.

Ejemplo concreto: el `ci.yml` real hoy tiene un job `lint` (hadolint sobre el Dockerfile)
que **no existe** en el template de referencia embebido en `.github/logsentinel-devsecops.agent.md`.
Un reemplazo total del archivo lo borraría silenciosamente — esto es una regresión inaceptable.

Proceso correcto para `harden-ci`:
1. Leer el `ci.yml` actual completo
2. Identificar qué falta vs. el checklist de `.github/copilot-instructions-devsecops.md`
3. Agregar `permissions:` en nivel raíz si falta
4. Agregar `timeout-minutes:` a cada job que no lo tenga
5. Reemplazar tags mutables (`@v4`, `@v3.1.0`) por SHA de 40 caracteres correspondiente
6. Agregar job de frontend tests si falta
7. NO tocar jobs existentes que ya funcionan (`lint`, `build`, etc.) salvo para agregar campos faltantes

## Proceso

Ejecutar el proceso de 6 pasos definido en `.github/logsentinel-devsecops.agent.md`
§ "Proceso de ejecución", con la regla de edición incremental de arriba como override
del Paso 3–4 cuando el artefacto ya existe.

Traducción de herramientas:
- Creación/edición de archivos → Write (nuevo) / Edit (existente, siempre incremental)
- Comandos de verificación (`grep`, `docker build`) → Bash
- Búsquedas de patrones para checklists → Grep / Glob

## Checklists de pre-escritura

Antes de escribir/editar cada tipo de artefacto, verificar el checklist correspondiente
definido en `.github/logsentinel-devsecops.agent.md` § Paso 3. No reproducirlo aquí
para evitar divergencia — leerlo del archivo original cada vez.

## Restricciones absolutas

- NUNCA hardcodear secretos en ningún artefacto (solo `${{ secrets.NAME }}` en workflows, `${ENV_VAR}` en compose/Docker)
- NUNCA usar `continue-on-error: true` en steps de seguridad
- NUNCA usar `:latest` como tag de imagen base
- NUNCA ejecutar containers como root en la etapa `production` del Dockerfile
- NUNCA hacer `git commit` ni `git push` — reportar al usuario el mensaje formateado en Conventional Commits
- NUNCA borrar jobs/steps existentes que ya funcionan sin aprobación explícita del usuario

## Contrato de salida

Al terminar, emitir el reporte del Paso 6 del proceso original,
seguido inmediatamente de este bloque estructurado:

```
---OUTPUT---
STATUS: GREEN | PARTIAL | BLOCKED
FILES_CHANGED:
  - path/relativo/al/repo/archivo.yml
  - path/relativo/al/repo/Dockerfile
TESTS: N/A (infrastructure)
ARCH_GATE: PASS | FAIL | N/A
DEVSECOPS_GATE: PASS | FAIL | PENDING
SUGGESTED_COMMIT: "tipo(scope): descripción del cambio"
ESCALATION_NOTE:
---END OUTPUT---
```

- `STATUS: GREEN` — artefactos generados y checklists satisfechos
- `STATUS: PARTIAL` — artefactos generados pero algún check externo (ej. NVD_API_KEY no configurada en GitHub Secrets) impide validación completa
- `STATUS: BLOCKED` — requiere acción del humano fuera del scope de este agente (ej. configurar Secrets en GitHub, crear Environments)
- `ESCALATION_NOTE` — solo si BLOCKED: describir exactamente qué acción manual se requiere
