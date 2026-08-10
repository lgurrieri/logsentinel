---
name: verify-clean-arch
description: >
  Architecture quality gate for LogSentinel. Verifies Clean Architecture rules
  and basic DevSecOps controls. Use before any PR or "check architecture",
  "validar dependencias", "verificar arquitectura".
---

# Skill: verify-clean-arch

## Propósito
Verifica que el código de LogSentinel cumple las reglas de Clean Architecture y los
controles básicos de DevSecOps. Usar cuando: "verificar arquitectura", "check pre-PR",
"validar dependencias", "revisar código antes de commit".

## Checks a ejecutar

### Check 1: Reglas de dependencia (analizar imports de cada archivo Java)

| Paquete | Import prohibido |
|---------|-----------------|
| `domain/**` | `org.springframework.*`, `jakarta.*`, `javax.*`, `com.logsentinel.infrastructure.*` |
| `application/**` | `com.logsentinel.infrastructure.*`, `org.springframework.data.*` |
| `infrastructure/adapters/in/web/**` | Retornar `@Entity` directamente (debe ser `record` DTO) |

Ejemplo de violación:
```java
// domain/model/Incident.java — VIOLACIÓN
import org.springframework.data.annotation.Id;  // PROHIBIDO
import jakarta.persistence.Entity;              // PROHIBIDO
```

### Check 2: Sin Lombok
```bash
grep -r "import lombok\|@Data\|@Getter\|@Setter\|@Builder\|@AllArgsConstructor\|@NoArgsConstructor" \
  backend/src/ --include="*.java"
```
Resultado esperado: **0 matches**.

### Check 3: DTOs como `record`
Todos los archivos en `infrastructure/adapters/in/web/dto/` deben ser `record`, no `class`:
```bash
grep -rn "^public class" backend/src/main/java/**/dto/*.java 2>/dev/null
```
Resultado esperado: **0 archivos**.

### Check 4: `equals()` en `@Entity` solo por `id`
Las clases `@Entity` con `equals()` deben comparar únicamente el campo `id`.
Patrón incorrecto — rompe proxies Hibernate:
```java
// ❌ Comparar múltiples campos
return Objects.equals(this.systemName, e.systemName)
    && Objects.equals(this.status, e.status);
```
Patrón correcto:
```java
// ✅
return id != null && id.equals(e.id);
```

### Check 5: Sin credenciales hardcodeadas
```bash
grep -rn "password\s*=\s*[\"'][^$]" backend/src/main/ --include="*.java"
grep -rn "api.key\s*=\s*[\"']" backend/src/main/ --include="*.java"
```
Resultado esperado: **0 matches**.
Las credenciales van en `application-{profile}.yml` via `${ENV_VAR}`.

### Check 6: `ProcessBuilder` con validación de input (US4)
Si existe `RemediationService.java`, verificar que el script es validado antes de ejecutarse.
Buscar `new ProcessBuilder` y confirmar que hay una llamada a un método de validación previo.

### Check 7: `SseEmitter.complete()` en bloque `finally`
Verificar que todos los usos de `SseEmitter` tienen `emitter.complete()` en un bloque `finally`
para evitar hilos huérfanos en Tomcat.

---

## Checks de seguridad de perímetro (DevSecOps)

### Check 8: Frontend — sin XSS via `dangerouslySetInnerHTML`
Los logs del stream SSE son untrusted input. React escapa automáticamente JSX, pero
`dangerouslySetInnerHTML` bypasea esa protección.

```bash
grep -rn "dangerouslySetInnerHTML" frontend/src/ --include="*.tsx" --include="*.ts"
```
Resultado esperado: **0 matches**.
Si se necesita renderizar Markdown del LLM, usar `marked` + `DOMPurify.sanitize()`.

### Check 9: Dockerfile — usuario non-root en etapa production
```bash
grep -n "^USER" backend/Dockerfile 2>/dev/null
```
Resultado esperado: al menos `USER logsentineluser` (uid 1001) **antes** de `ENTRYPOINT`.
Si el archivo no existe, reportar como pendiente (no como fallo del código existente).

### Check 10: Sin secretos en capas del Dockerfile
```bash
grep -nE "^ENV.*(KEY|PASSWORD|SECRET|TOKEN)" backend/Dockerfile 2>/dev/null
```
Resultado esperado: **0 matches**.
Los secretos se inyectan en runtime vía `docker run -e`, nunca en tiempo de build.

### Check 11: Workflows GitHub Actions con `permissions:` declarado
```bash
find .github/workflows/ -name "*.yml" -exec \
  grep -L "^permissions:" {} \; 2>/dev/null
```
Resultado esperado: **0 archivos** (todos tienen `permissions:` a nivel raíz).
Sin declaración explícita, GitHub hereda `write-all` si el repo lo permite.

### Check 12: Actions de terceros pineadas a SHA (no tags mutables)
```bash
grep -rn "uses: " .github/workflows/ --include="*.yml" | \
  grep -v "@[0-9a-f]\{40\}" | grep -v "uses: \.\/" 2>/dev/null
```
Resultado esperado: **0 matches** (todas las Actions externas pineadas a SHA de 40 chars).

---

## Check 13: Cumplimiento de contrato OpenAPI (BLOQUEANTE)

Ejecutar el skill `.github/skills/verify-openapi-contract/SKILL.md` sobre los endpoints
tocados por el cambio bajo revisión: localizar cada path/schema relevante en
`docs/openapi: 3.0.yml` y comparar método HTTP, path, nombres/tipos de campos, enums y
status codes contra lo efectivamente implementado.

Resultado esperado: **`OK`** (coincide, o la discrepancia ya está documentada como
excepción cruzada patrón `KNOWN ISSUE`) — cualquier discrepancia NO documentada es
`DRIFT_DETECTED` y bloquea la aprobación hasta relevamiento + decisión humana explícita
(ver protocolo de escalamiento del skill `verify-openapi-contract`).

## Formato del informe de salida
```
=== LogSentinel Architecture + Security Report ===
✅ Reglas de dependencia: OK
❌ Lombok: 2 violaciones → Runbook.java:15, RunbookChunk.java:8
✅ DTOs como records: OK
⚠️  equals() en @Entity: revisar IncidentAnalysis.java (compara campo 'incidentId')
✅ Credenciales hardcodeadas: OK
✅ ProcessBuilder validado: OK
✅ SseEmitter.complete() en finally: OK
--- DevSecOps ---
✅ dangerouslySetInnerHTML: OK
✅ Dockerfile usuario non-root: OK
✅ Sin secretos en ENV layers: OK
⚠️  Workflows sin permissions:: ci.yml (no declarado)
✅ Actions pineadas a SHA: OK
--- Contrato OpenAPI ---
✅ Check 13 Contrato OpenAPI: OK

RESULTADO: FAIL — 1 error crítico, 2 advertencias
Resolver antes de abrir el PR.
```

## Criterio de aprobación
- 0 errores críticos (Check 1, 2, 3, 5, 8)
- 0 advertencias bloqueantes (Check 4, 6, 7, 9, 10, 11, 12)
- Check 13 en `OK` — `DRIFT_DETECTED` es bloqueante al mismo nivel que un error crítico,
  sin excepción, hasta relevamiento + aprobación humana explícita

Los checks 9–12 solo aplican si los artefactos de infraestructura existen.
Si `backend/Dockerfile` o `.github/workflows/` no existen, reportar como "pendiente" no como fallo.

## Racionalizaciones comunes

| Racionalización | Realidad |
|----------------|----------|
| "Es solo temporal, lo corrijo después" | La deuda técnica de arquitectura se compone. Un import prohibido hoy genera 10 imports prohibidos en un mes. |
| "El test pasa igual" | Los tests no verifican dependencias de paquete. La violación existe aunque los tests pasen. |
| "No es urgente, el feature funciona" | La arquitectura rota es invisible hasta que escala. Cuando escale será imposible refactorizar. |
| "Agrego Lombok porque agiliza" | El proyecto tiene una decisión explícita de no usar Lombok (Java 25 tiene records). Violarlo crea inconsistencia de codebase. |

## Red Flags (DETENER el trabajo inmediatamente)

Estas violaciones indican que hay que parar y corregir ANTES de continuar:
- `@Autowired` encontrado en cualquier capa
- `import lombok.Data` en una entidad `@Entity`
- Un `@RestController` que retorna un tipo `@Entity` directamente
- `@MockBean` en un test que extiende solo `@ExtendWith(MockitoExtension.class)` (indica violación de capas)
- `import com.logsentinel.infrastructure.*` en `application/usecases/`
- Un endpoint implementado con path/schema/enum que contradice `docs/openapi: 3.0.yml`
  sin relevamiento ni aprobación humana documentada (Check 13)

## Verificación (checklist de salida)

### Clean Architecture (backend)
- [ ] Check 1 Dependencias: 0 violaciones encontradas
- [ ] Check 2 Lombok: `grep` retorna 0 matches
- [ ] Check 3 DTOs como records: `grep` retorna 0 archivos
- [ ] Check 4 equals() en @Entity: comparación solo por `id`
- [ ] Check 5 Credenciales: 0 matches
- [ ] Check 6 ProcessBuilder: validación de input presente
- [ ] Check 7 SseEmitter.complete(): presente en bloque `finally`

### DevSecOps (perímetro)
- [ ] Check 8 dangerouslySetInnerHTML: 0 matches en frontend
- [ ] Check 9 Dockerfile usuario non-root: `USER logsentineluser` presente
- [ ] Check 10 Sin ENV secretos: 0 matches en Dockerfile
- [ ] Check 11 Workflows con `permissions:`: 0 archivos sin declarar
- [ ] Check 12 Actions pineadas a SHA: 0 Actions con tags mutables

### Contrato OpenAPI (bloqueante)
- [ ] Check 13 Contrato OpenAPI: `OK` — sin discrepancias, o excepción documentada
      (`KNOWN ISSUE` cruzado) referenciada explícitamente

**RESULTADO FINAL**: PASS (todos los checks superados, incluido Check 13) o FAIL con lista de archivos a corregir.
