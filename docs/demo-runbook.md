# Runbook de Demo — LogSentinel (flujo end-to-end)

Guía paso a paso para demostrar en vivo el flujo completo del producto: reporte de un
incidente (US1) → diagnóstico de IA con RAG en streaming (US2/US3) → ejecución
auditada de la remediación sugerida (US4).

> Todos los comandos asumen que estás parado en la raíz del repo (`logsentinel/`),
> salvo que se indique lo contrario.

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
