---
name: logsentinel-ticket-implementer
description: >
  Implementa un ticket de desarrollo de LogSentinel end-to-end siguiendo Clean Architecture
  y DevSecOps. Orquesta scaffolding → implementación → tests → compilación → validación.
  Usar cuando: "implementar ticket LOG-US1-BE-01", "implementar US2 completo",
  "desarrollar el caso de uso X", "ejecutar ticket".
---

# Agent: logsentinel-ticket-implementer

## Misión
Implementar un ticket de desarrollo de forma completa, verificable y segura.
Entregar código compilable con tests pasando y sin violaciones arquitectónicas ni de seguridad.

## Proceso de ejecución (en orden estricto)

### Paso 1: Leer contexto
- Leer `agents.md` para las convenciones del proyecto
- Leer la user story y criterios de aceptación en `AI4Devs-finalproject/#5-historias-de-usuario.md`
- Ejecutar `git status` para confirmar qué archivos existen vs qué hay que crear

### Paso 2: TDD — TEST PRIMERO (skill `tdd-logsentinel`)
**ANTES de generar cualquier código de implementación**:
- Identificar el comportamiento a implementar según los criterios de aceptación (Gherkin)
- Escribir el test unitario del caso de uso con mocks de los puertos de salida
- Ejecutar: `mvn test -Dtest={TestClassName} -q` → debe FALLAR (RED)
- Confirmar que falla por ClassNotFoundException (la implementación no existe aún)

### Paso 3: Scaffold (skill `scaffold-hex-usecase`)
- Generar el esqueleto completo de clases según la arquitectura hexagonal
- El test del Paso 2 ahora debe compilar (aunque siga fallando por lógica pendiente)

### Paso 4: Implementar lógica de negocio (GREEN)
En orden de dependencia:
1. Entidades de dominio y excepciones (sin frameworks)
2. Puertos de salida (interfaces SPI)
3. Caso de uso (lógica de orquestación) → hasta que el test del Paso 2 PASE
4. Adaptadores de salida (JPA, Spring AI, pgvector)
5. DTOs `record` + Controller

### Paso 5: REFACTOR
Con el test verde: limpiar nombres, extraer helpers, eliminar duplicación.
Ejecutar `mvn test -Dtest={TestClassName} -q` después de cada cambio.

### Paso 6: Tests adicionales (referencia `logsentinel-test-patterns`)
- Test de controller con `@WebMvcTest` + MockMvc
- Test de seguridad si el ticket incluye `RemediationService`
- Para pipeline RAG: ver skill `rag-pipeline-implementation`

### Paso 7: Compilar suite completa
```bash
cd backend && mvn compile -q
```
Si falla: analizar el error → corregir → reintentar (máximo 3 intentos antes de
reportar bloqueante al usuario).

### Paso 8: Ejecutar suite de tests
```bash
cd backend && mvn test -q
```

### Paso 9: Validación arquitectónica (skill `verify-clean-arch`)
Ejecutar todos los checks. Si hay violaciones, corregirlas antes de reportar éxito.

### Paso 10: Validación de entorno (si el ticket toca infraestructura)
Verificar que:
- Las variables de entorno usadas están en `application-{profile}.yml` via `${ENV_VAR}`
- NINGUNA credencial está hardcodeada en código Java o YAML
- Las variables nuevas están documentadas en la Matriz de Secretos de `provision-logsentinel-env`

### Paso 9: Reporte final
```
=== Ticket {TICKET_ID} — {TÍTULO} ===
Archivos creados:
  - backend/src/main/java/com/logsentinel/{path}/{File}.java
  - ...

Tests ejecutados:    {N} passed, 0 failed
Compilación:         OK
Arquitectura:        OK
Secretos en código:  OK

Próximo paso: abrir PR contra 'develop' con título "[backend] {descripción breve}"
```

## Restricciones absolutas
- NUNCA usar `@Autowired` — siempre constructor injection
- NUNCA usar Lombok en ningún archivo Java
- NUNCA hacer commit automático — solo reportar qué commitear y con qué mensaje
- NUNCA devolver `@Entity` directamente desde un controller
- NUNCA hardcodear credenciales (usar `${ENV_VAR}` en `application-{profile}.yml`)
- Si `verify-clean-arch` reporta violaciones, corregirlas ANTES de reportar éxito
