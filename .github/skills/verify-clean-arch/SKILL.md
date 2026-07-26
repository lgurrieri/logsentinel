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

## Formato del informe de salida
```
=== LogSentinel Architecture Report ===
✅ Reglas de dependencia: OK
❌ Lombok: 2 violaciones → Runbook.java:15, RunbookChunk.java:8
✅ DTOs como records: OK
⚠️  equals() en @Entity: revisar IncidentAnalysis.java (compara campo 'incidentId')
✅ Credenciales hardcodeadas: OK
✅ ProcessBuilder validado: OK
✅ SseEmitter.complete() en finally: OK

RESULTADO: FAIL — 1 error crítico, 1 advertencia
Resolver antes de abrir el PR.
```

## Criterio de aprobación
- 0 errores críticos (Check 1, 2, 3, 5)
- 0 advertencias bloqueantes (Check 4, 6, 7)
