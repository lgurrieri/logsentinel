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

## Verificación (checklist de salida)

Resultado esperado para cada check:
- [ ] Check 1 Dependencias: 0 violaciones encontradas
- [ ] Check 2 Lombok: `grep` retorna 0 matches
- [ ] Check 3 DTOs como records: `grep` retorna 0 archivos
- [ ] Check 4 equals() en @Entity: comparación solo por `id`
- [ ] Check 5 Credenciales: 0 matches
- [ ] Check 6 ProcessBuilder: validación de input presente
- [ ] Check 7 SseEmitter.complete(): presente en bloque `finally`

**RESULTADO FINAL**: PASS (todos los checks superados) o FAIL con lista de archivos a corregir.
