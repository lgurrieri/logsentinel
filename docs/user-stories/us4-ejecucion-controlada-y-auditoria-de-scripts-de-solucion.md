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
* **Small:** Se centra en guardar el script, disparar su ejecución, capturar la consola y actualizar el estado del incidente a `RESOLVED` si la ejecución concluye exitosamente (el estado `FAILED` corresponde al `executionStatus` del registro de auditoría, no al `status` del incidente — el contrato solo define `OPEN`/`IN_PROGRESS`/`RESOLVED`/`CLOSED` para el incidente).
* **Testable:** Sí. Mediante pruebas automatizadas inyectando un script mock (ej: `echo 'success'`) y validando el contenido de los logs grabados.

### Criterios de Aceptación (Gherkin)

```gherkin
Escenario: Ejecución exitosa de un script de remediación generado por la IA
  Dado que existe un análisis guardado con un script de solución sugerido
  Cuando el SRE presiona el botón "Ejecutar Remediación" en la interfaz web
  Entonces el backend crea de inmediato un registro en la tabla "remediation_actions" en estado "EXECUTING"
  Y al finalizar la ejecución, el registro se actualiza a estado "SUCCESS" o "FAILED" según el código de salida del proceso
  Y el estado del incidente principal debe actualizarse automáticamente a "RESOLVED" si el código de salida del proceso es cero (0).

```

### Especificación Técnica de Implementación

* **Aislamiento y Ejecución:** La capa de servicio mapea el script a través de un componente ejecutor especializado (ej: `ProcessBuilder` de Java encapsulado de manera segura para prevenir vulnerabilidades de inyección de comandos).
* **Persistencia:** Captura el flujo de salida estándar (`stdout`) y el flujo de errores (`stderr`) combinándolos en un solo campo de texto para rellenar la columna `execution_log` de la tabla `remediation_actions`.
* **Transaccionalidad:** Se aplican dos transacciones independientes secuenciales con `Propagation.REQUIRES_NEW` de Spring, para que el registro de auditoría sea inmune a una caída catastrófica del hilo principal durante la ejecución del script: la Transacción A commitea de inmediato el estado `EXECUTING` (antes de invocar el sandbox), y la Transacción B commitea el cierre con los buffers finales de `stdout`/`stderr` y el estado `SUCCESS` o `FAILED`, una vez que la ejecución aislada concluye.