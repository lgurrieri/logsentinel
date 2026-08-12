# Runbook de Demo — LogSentinel (flujo end-to-end)

Guía paso a paso para demostrar en vivo el flujo completo del producto: reporte de un
incidente (US1) → diagnóstico de IA con RAG en streaming (US2/US3) → ejecución
auditada de la remediación sugerida (US4).

> Todos los comandos asumen que estás parado en la raíz del repo (`logsentinel/`),
> salvo que se indique lo contrario.

> **Dos modos de demo**: local (secciones 1–7, contra `localhost`) o en una VM de
> Azure ya desplegada (sección 8, contra la URL pública). Si la VM ya está
> corriendo, saltá directo a la sección 8 y usá esa URL en los pasos 4–6 en vez de
> `localhost:5173`.

---

## 1. Prerequisitos

- Java 25 (Temurin), Maven 3.9+
- Docker Desktop corriendo
- Node.js + npm (para el frontend)
- `curl` y `python3` (para el paso 3 — sembrar un runbook de ejemplo)
- **Ollama instalado nativamente en el equipo** (recomendado): evita la descarga en
  frío de la imagen + modelos (~5GB) del servicio `ollama` dockerizado, que puede
  tardar minutos u horas según el ancho de banda (ver `DEBT-005` en
  `docs/deuda-tecnica.md`). Con Ollama nativo instalado, corré una única vez:
  ```bash
  ollama pull llama3.1
  ollama pull nomic-embed-text
  ```
  Si no tenés Ollama nativo, podés usar igual el servicio `ollama` del
  `docker-compose.yml` (`docker compose up -d db ollama`) — pero no es recomendable
  para una demo en vivo por el tiempo de descarga inicial.

---

## 2. Levantar el entorno

Con Ollama nativo ya corriendo en `localhost:11434`, solo hace falta Postgres en
Docker; backend y frontend corren directo en el host.

1. Base de datos (Postgres 16 + pgvector):
   ```bash
   docker compose up -d db
   ```
2. Variables de entorno del backend (si no existe todavía):
   ```bash
   cp backend/.env.example backend/.env
   ```
3. **Terminal A** — backend:
   ```bash
   cd backend
   SPRING_PROFILES_ACTIVE=dev,ollama mvn spring-boot:run
   ```
   Esperar a que loguee el arranque, y verificar:
   ```bash
   curl http://localhost:8080/actuator/health
   # {"status":"UP",...}
   ```
4. **Terminal B** — frontend:
   ```bash
   cd frontend
   npm install   # solo la primera vez
   npm run dev
   ```
   Abrir `http://localhost:5173`.

---

## 3. Sembrar un runbook de ejemplo (corpus RAG de US2)

LogSentinel todavía no tiene un endpoint de ingesta de runbooks (fuera del alcance de
US1–US4) — la tabla `runbook_chunks` empieza vacía y se carga hoy directo en base.
Para que el diagnóstico de IA del paso 5 tenga un runbook real que citar (en vez de
que el LLM alucine sin contexto), sembrá al menos uno **antes** de reportar el
incidente de ejemplo.

```bash
# 1. Generar un embedding real (768 dim, nomic-embed-text) para el contenido del runbook
EMBEDDING=$(curl -s http://localhost:11434/api/embeddings \
  -d '{"model": "nomic-embed-text", "prompt": "connection pool exhausted auth-service"}' \
  | python3 -c "import json,sys; print(json.dumps(json.load(sys.stdin)['embedding'], separators=(',',':')))")

# 2. Insertarlo en runbook_chunks vía el servicio "db" del compose
docker compose exec -T db psql -U logsentinel -d logsentinel -v ON_ERROR_STOP=1 <<SQL
INSERT INTO runbook_chunks (content, embedding) VALUES (
  \$\$Runbook: agotamiento del pool de conexiones en auth-service. Sintoma tipico en los logs: "connection pool exhausted after Nms". Causa raiz habitual: conexiones no liberadas correctamente tras timeouts prolongados bajo carga sostenida. Remediacion recomendada: reciclar el pool de conexiones del servicio ejecutando: echo 'auth-service pool recycled'.\$\$,
  '$EMBEDDING'::vector
);
SQL
```

> Nota: el comando de remediación del runbook usa `echo` a propósito — es el único
> comando de la allowlist del sandbox (`echo,ansible-playbook,systemctl`, ver
> `application.yml` → `logsentinel.sandbox.allowlist`) que va a ejecutar
> correctamente en cualquier máquina sin depender de infraestructura real
> (`systemctl` no existe en macOS). El system prompt de la IA (`StreamDiagnosticService`)
> instruye explícitamente "no inventes soluciones fuera de este contexto", así que
> con temperatura 0.2 el modelo tiende a reutilizar ese mismo comando.

Verificar que quedó insertado:
```bash
docker compose exec -T db psql -U logsentinel -d logsentinel -c "SELECT count(*) FROM runbook_chunks;"
```

---

## 4. US1 — Reportar un incidente

1. Ir a `http://localhost:5173/` (formulario "Reportar incidente").
2. Completar:
   - **Sistema**: `auth-service` (desplegable)
   - **Urgencia**: `Crítica` (radio button)
   - **Volcado de logs** (textarea): pegar el siguiente log de ejemplo — coincide
     semánticamente con el runbook sembrado en el paso 3:
     ```
     2026-08-11 10:32:14 ERROR [auth-service] HikariPool-1 - Connection is not available, request timed out after 30000ms. ERROR: connection pool exhausted after 30000ms. Failed to acquire JDBC connection for /api/login.
     ```
3. Click **"Reportar incidente"**.
4. Redirige automáticamente a `/incidents/{id}/dashboard`.

---

## 5. US2/US3 — Ver el diagnóstico de IA en vivo (streaming RAG)

En el dashboard, la Terminal de Diagnóstico va mostrando el estado en el header
(punto de color + texto) y el texto llega token a token:

`Conectando con el agente SRE...` → `Analizando incidente...` (cursor `_`
parpadeando, texto Markdown renderizándose en vivo) → `Diagnóstico completado`.

El texto debería referenciar el runbook sembrado (pool de conexiones / auth-service),
porque US2 lo recuperó por similitud semántica real (pgvector, distancia coseno) y
se lo pasó como contexto obligatorio al LLM.

> **Honestidad sobre el determinismo**: esta es una inferencia real de
> Ollama/llama3.1 (no un mock) — el texto exacto puede variar levemente entre
> corridas. Si el modelo no incluye un bloque de código con &#96;&#96;&#96; en su
> respuesta, la sección "Remediación sugerida" del paso 6 va a mostrar *"No hay
> ningún script de remediación disponible para este incidente todavía"*
> (`SuggestedScriptExtractor` solo persiste un script si hay un bloque de código
> cerrado). En ese caso: crear el incidente de nuevo (paso 4) — con temperatura 0.2
> y el runbook sembrado como contexto, en la práctica el modelo converge rápido a
> repetir el comando `echo` sugerido.

---

## 6. US4 — Ejecutar la remediación con auditoría

1. Debajo del diagnóstico aparece **"Remediación sugerida"** con el script en un
   bloque de código estático.
2. Click **"Ejecutar Script de Remediación"** (botón rojo).
3. Aparece el modal **"Confirmación requerida"**: *"¿Confirmas la ejecución de este
   comando en el sistema de producción? Esta acción quedará registrada bajo tu firma
   de auditoría."* — el foco por defecto está en "Cancelar" (doble confirmación
   intencional, ver `LOG-US4-FE-03`). Click **"Confirmar Ejecución"**.
4. Aparece la Terminal de Salida: `Ejecutando script en la infraestructura
   simulada...` → al resolver, `✓ Ejecución exitosa` (stdout en gris) o
   `✗ Ejecución fallida` (stderr en rojo, prefijado `[ERROR]`) si el comando cayera
   fuera de la allowlist o fallara.

Verificación de auditoría en base (opcional, para mostrar el trazo completo):
```bash
docker compose exec -T db psql -U logsentinel -d logsentinel -c \
  "SELECT execution_status, stdout_log, stderr_log FROM remediation_actions ORDER BY created_at DESC LIMIT 1;"
docker compose exec -T db psql -U logsentinel -d logsentinel -c \
  "SELECT status FROM incidents ORDER BY created_at DESC LIMIT 1;"
# status debe ser RESOLVED si el script salió con código 0.
```

---

## 7. Limpieza post-demo (opcional)

```bash
docker compose exec -T db psql -U logsentinel -d logsentinel -c \
  "TRUNCATE remediation_actions, incident_diagnostics, incidents, runbook_chunks CASCADE;"
```
Luego `Ctrl+C` en las terminales de backend/frontend, y `docker compose down` si no
querés dejar la base corriendo.

---

## 8. Despliegue en Azure

Corre el stack containerizado sin cambios (`db`+`ollama`+`backend` de
`docker-compose.yml`) en una VM de Azure, más un overlay `docker-compose.prod.yml`
que agrega Nginx (único punto de entrada público, puerto 80, con Basic Auth) y
reemplaza el `build:` del backend por la imagen ya construida en GHCR. Ver
`docs/deuda-tecnica.md` (`DEBT-006`/`007`/`008`) para las limitaciones aceptadas
a propósito para esta demo.

### 8.1 Aprovisionar la VM (una sola vez)

1. `az login` interactivo y confirmar la suscripción correcta:
   ```bash
   az account show --query "{name:name, id:id}" -o table
   ```
2. Chequear cupo de la región/tamaño de VM elegidos (`brazilsouth` preferido por
   latencia; `eastus2` como fallback si no hay cupo):
   ```bash
   az vm list-usage --location brazilsouth -o table
   az vm list-skus --location brazilsouth --size Standard_D4as_v4 --all -o table
   ```
3. Generar un par de claves SSH **dedicado** (no reusar claves personales):
   ```bash
   ssh-keygen -t ed25519 -f ./logsentinel-vm -C "logsentinel-demo"
   ```
4. Exportar las variables requeridas por `IaC/scripts/provision-vm.sh` (ver el
   encabezado del script para la lista completa: `RESOURCE_GROUP`, `LOCATION`,
   `VM_NAME`, `DNS_LABEL`, `ADMIN_USER`, `SSH_PUBLIC_KEY_PATH`,
   `POSTGRES_PASSWORD`, `NGINX_BASIC_AUTH_USER`, `NGINX_BASIC_AUTH_PASS`,
   `MY_PUBLIC_IP` — `curl -s ifconfig.me` —, `BUDGET_AMOUNT_ARS`,
   `BUDGET_ALERT_EMAIL`) y correrlo:
   ```bash
   ./IaC/scripts/provision-vm.sh
   ```
   Crea, en orden: resource group, budget con alerta al 80%/100%, VNet+NSG (SSH
   acotado a `MY_PUBLIC_IP`, puerto 80 público, resto denegado), IP pública
   Standard con DNS label estático, la VM (cloud-init instala Docker y deja
   `.env`/`.htpasswd` listos), y una Automation Account con rol `Virtual Machine
   Contributor` acotado solo a esta VM.
5. Subir los runbooks de la Automation Account creada:
   - `IaC/scripts/automation-start.ps1` (enciende la VM + smoke test real dentro
     de ella antes de reportar éxito)
   - `IaC/scripts/automation-stop.ps1` (`Stop-AzVM -Deallocate`)
   Crear en la Automation Account las variables `ResourceGroupName`/`VMName` y la
   credencial `NginxBasicAuth`, y programar los schedules: **10:30 ART** (start) /
   **16:15 ART** (stop).

### 8.2 Configurar GitHub para el deploy continuo

1. Crear el Environment `production` (Settings → Environments) con estos 5
   secrets:

   | Secret | Contenido |
   |---|---|
   | `AZURE_VM_HOST` | DNS label fijo de la VM (`{DNS_LABEL}.{LOCATION}.cloudapp.azure.com`) |
   | `AZURE_VM_SSH_USER` | `ADMIN_USER` usado en el provisioning |
   | `AZURE_VM_SSH_PRIVATE_KEY` | Contenido de `./logsentinel-vm` (la clave privada dedicada del paso 8.1.3) |
   | `POSTGRES_PASSWORD` | El mismo valor exportado como `POSTGRES_PASSWORD` en 8.1.4 |
   | `NGINX_BASIC_AUTH_USER` / `NGINX_BASIC_AUTH_PASS` | Los mismos valores de 8.1.4 |

2. **Settings → Actions → General → Workflow permissions** → marcar "Read and
   write permissions" (sin esto, el push a GHCR falla con 403).
3. No hace falta marcar el paquete GHCR como público: el job `deploy` de
   `cd.yml` autentica la VM contra `ghcr.io` con el mismo `GITHUB_TOKEN` del
   job antes de hacer `docker compose pull`.

### 8.3 Desplegar

1. Disparar manualmente el workflow `.github/workflows/cd.yml`
   (`workflow_dispatch` — Actions → CD → Run workflow). Build de la imagen
   backend + push a GHCR, build del frontend (con `VITE_API_BASE_URL=""` para que
   quede same-origin vía Nginx), y deploy por SSH/SCP a la VM.
2. Verificar que los 4 servicios estén healthy:
   ```bash
   ssh -i ./logsentinel-vm "${ADMIN_USER}@${DNS_LABEL}.${LOCATION}.cloudapp.azure.com" \
     'cd /opt/logsentinel && docker compose -f docker-compose.yml -f docker-compose.prod.yml ps'
   ```
3. Recorrer el flujo completo de las secciones 3–6 de este runbook contra la URL
   pública (`https://{DNS_LABEL}.{LOCATION}.cloudapp.azure.com`, con las
   credenciales de `NGINX_BASIC_AUTH_USER`/`PASS` cuando el navegador las pida),
   **antes** del horario de la demo — no alcanza con confiar en el smoke test
   automático del arranque.
4. Confirmar en Azure Cost Management el gasto acumulado y la moneda real de
   facturación dentro de la primera hora.

### 8.4 Operación diaria (una vez ya desplegado)

La Automation Account enciende/apaga la VM sola (10:30/16:15 ART). Para un
redeploy de código nuevo, repetir solo el paso 8.3.1. Para apagar/prender fuera
de horario manualmente:
```bash
az vm start --resource-group "${RESOURCE_GROUP}" --name "${VM_NAME}"
az vm deallocate --resource-group "${RESOURCE_GROUP}" --name "${VM_NAME}"
```
