## US4: Ejecución Controlada y Auditoría de Scripts de Solución

### Narrativa

> **Como** Ingeniero SRE
> **Quiero** activar de forma manual el script de mitigación sugerido por la IA y ver sus trazas de salida
> **Para** resolver el incidente de forma segura dejando constancia en el historial de auditoría de la plataforma.

### Evaluación INVEST

* **Independent:** Depende de la generación del análisis en US3, pero la lógica de ejecución del comando y el registro del resultado en la base de datos es una unidad de software separada.
* **Negotiable:** El entorno de ejecución está acotado y simula llamadas seguras a scripts del sistema o playbooks de automatización configurados previamente.
* **Valuable:** Crítica. Cierra el bucle de reparación y provee el insumo para futuras métricas de cumplimiento (*Compliance*).
* **Estimable:** Sí. Involucra el uso del API de ejecución de procesos o comandos de Java.
* **Small:** Se centra en guardar el script, disparar su ejecución, capturar la consola y actualizar los estados del incidente a `RESOLVED` o `FAILED`.
* **Testable:** Sí. Mediante pruebas automatizadas inyectando un script mock (ej: `echo 'success'`) y validando el contenido de los logs grabados.

### Criterios de Aceptación (Gherkin)

```gherkin
Escenario: Ejecución exitosa de un script de remediación generado por la IA
  Dado que existe un análisis guardado con un script de solución sugerido
  Cuando el SRE presiona el botón "Ejecutar Remediación" en la interfaz web
  Entonces el backend crea un registro en la tabla "remediation_actions" en estado "DRY_RUN" o "SUCCESS"
  Y el estado del incidente principal debe actualizarse automáticamente a "RESOLVED" si el código de salida del proceso es cero (0).

```

### Especificación Técnica de Implementación

* **Aislamiento y Ejecución:** La capa de servicio mapea el script a través de un componente ejecutor especializado (ej: `ProcessBuilder` de Java encapsulado de manera segura para prevenir vulnerabilidades de inyección de comandos).
* **Persistencia:** Captura el flujo de salida estándar (`stdout`) y el flujo de errores (`stderr`) combinándolos en un solo campo de texto para rellenar la columna `execution_log` de la tabla `remediation_actions`.
* **Transaccionalidad:** Se aplica la anotación `@Transactional` de Spring para asegurar que la actualización del estado del incidente y la inserción de la acción de remediación se ejecuten de manera atómica en la base de datos PostgreSQL.