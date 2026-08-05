---
name: debug-react-logsentinel
description: >
  Debugging sistemático para los fallos más frecuentes del frontend de LogSentinel:
  EventSource leaks, SSE sin datos, re-renders en cascada, Context que no actualiza,
  CORS, y Tailwind sin estilos. Usar cuando: "SSE no funciona", "terminal no renderiza",
  "EventSource leak", "re-renders infinitos", "CORS error frontend", "Tailwind no aplica".
---

# Debug Frontend — LogSentinel

## Overview

Cada bug tiene síntomas. Mapear el síntoma al árbol de diagnóstico correcto.
No debuggear de memoria — abrir las herramientas antes de editar código.

## Árbol 1 — SSE: el terminal no recibe datos

```
¿Ves eventos en Network > Filter "EventStream"?
  │
  ├── NO → EventSource no conecta
  │     ├── ¿console.log(url) antes de new EventSource() muestra URL correcta?
  │     │     └── NO → verificar VITE_API_BASE_URL en .env.local (no .env)
  │     ├── ¿Hay error CORS en la consola? → ir a Árbol 3
  │     └── ¿El backend responde? → curl http://localhost:8080/actuator/health
  │
  └── SÍ → EventSource conecta pero los chunks no llegan al estado
        ├── ¿source.onmessage está asignado? → verificar que no es null antes del primer evento
        ├── ¿Cuál es el payload raw? → Network > EventStream > clic en el evento
        │     ├── "data: {...}" → el frontend hace JSON.parse correctamente?
        │     │     └── Si JSON.parse lanza → usar try/catch y fallback a e.data
        │     └── "data: texto plano" → NO hacer JSON.parse, usar e.data directamente
        └── ¿El dispatch llega al reducer? → agregar log temporal en el reducer
```

**Causa más frecuente:** El backend envía texto plano y el frontend hace `JSON.parse(e.data)` que lanza silenciosamente. Solución:
```typescript
source.onmessage = (e) => {
  try {
    const parsed = JSON.parse(e.data) as { chunk?: string };
    dispatch({ type: 'RECEIVE_CHUNK', payload: parsed.chunk ?? e.data });
  } catch {
    dispatch({ type: 'RECEIVE_CHUNK', payload: e.data }); // fallback texto plano
  }
};
```

## Árbol 2 — EventSource leak (chunks duplicados en desarrollo)

**Síntoma:** El terminal recibe cada chunk DOS veces en modo dev.

```
¿StrictMode está activo en main.tsx?
  └── SÍ (siempre en desarrollo) → StrictMode monta → desmonta → remonta
        ¿El useEffect retorna cleanup?
          ├── NO → AQUÍ está el leak
          │     FIX: agregar return () => source.close(); al final del useEffect
          └── SÍ pero sigue duplicando
                ¿El cleanup cierra la instancia correcta?
                  └── Verificar que source es la variable del closure actual
                      NO usar useRef para el source si el useEffect tiene [id] en deps
```

**Fix correcto:**
```typescript
useEffect(() => {
  const source = new EventSource(url);
  source.onmessage = (e) => dispatch(...);
  return () => source.close(); // ← obligatorio
}, [incidentId]);
```

**Verificar fix:** Instalar React DevTools → Profiler → verificar que EventSource se instancia una sola vez por incidentId.

## Árbol 3 — CORS: error al conectar frontend con backend

```
¿La consola dice "blocked by CORS policy"?
  └── SÍ
        ¿La URL tiene el host correcto?
          ├── NO → verificar .env.local: VITE_API_BASE_URL=http://localhost:8080
          └── SÍ
                ¿El backend permite el origen http://localhost:5173?
                  ├── NO → en Spring Boot agregar:
                  │         @CrossOrigin(origins = "http://localhost:5173")
                  │         o configuración global de CorsConfiguration
                  └── SÍ pero aún falla en SSE
                        → EventSource NO envía OPTIONS preflight
                        → Verificar que el backend permite GET desde el origen
                        → En application-dev.yml: allowed-origins: http://localhost:5173
```

## Árbol 4 — Context: re-renders en cascada

**Síntoma:** Al recibir cada chunk SSE, componentes fuera del LogTerminal re-renderizan.

```
1. Instalar React DevTools → Profiler → Record → recibir un chunk → Stop
   ¿Qué componentes tienen barra de flamegraph larga?

2. ¿Re-renderizan componentes fuera de la feature incidents?
   └── SÍ → IncidentProvider está demasiado arriba en el árbol
         Mover IncidentProvider para que envuelva solo la feature incidents,
         NO todo AppProvider

3. ¿El propio Provider re-renderiza con cada chunk?
   └── SÍ → el objeto `value` se recrea en cada render
         Verificar que el value usa las referencias estables del useReducer:
         const value = { state, dispatch }; // dispatch es estable — no necesita useMemo
         Solo agregar useMemo si el Profiler CONFIRMA el problema (no antes)
```

## Árbol 5 — Context: dispatch no actualiza la UI

**Síntoma:** Se llama `dispatch` pero el componente no re-renderiza con el nuevo estado.

```
1. ¿El componente usa useIncident() en lugar de useContext(IncidentContext) directamente?
   → Si usa useContext directamente: refactorizar al hook

2. ¿El componente está dentro del IncidentProvider en el árbol de componentes?
   → Agregar log temporal en el Provider render para confirmar

3. ¿El action.type coincide EXACTAMENTE con el case del switch en el reducer?
   → TypeScript debería atrapar esto — verificar que no hay 'as' silenciando errores

4. ¿El reducer devuelve un nuevo objeto (inmutabilidad)?
   → ❌ state.diagnosticBuffer += payload       (mutación directa — React no detecta cambio)
   → ✅ return { ...state, diagnosticBuffer: state.diagnosticBuffer + action.payload }
```

## Árbol 6 — Tailwind: clases sin estilo visual

```
¿Las clases están presentes en el DOM? (Inspeccionar elemento)
  ├── NO → Vite no procesó el archivo → reiniciar npm run dev
  └── SÍ pero sin estilo
        ¿Es una clase de tema dark como text-green-400?
          └── ¿El elemento root tiene la clase `dark`?
                └── NO → verificar UIContext: la clase `dark` debe estar en <html> o en el wrapper root

        ¿Es una clase con valor arbitrario como bg-[#00ff00]?
          └── Verificar sintaxis exacta — sin espacios dentro de los corchetes

        ¿El contenido del archivo no está en el `content` de tailwind.config.ts?
          └── Verificar: content: ['./index.html', './src/**/*.{ts,tsx}']
              Si hay archivos fuera de src/ que usan clases Tailwind, agregarlos
```

## Red Flags

- Debuggear SSE editando código sin abrir Network tab > EventStream primero
- Agregar `console.log` dentro del render de un componente (causa re-renders adicionales)
- Usar `debugger` en un hook con EventSource activo (pausa el stream durante la inspección)
- Asumir que el bug es del backend sin verificar el Network tab del browser primero
- Agregar `useMemo`/`useCallback` sin evidencia del Profiler de que hay problema de rendimiento

## Verificación

Después de aplicar el fix:
- [ ] El síntoma original desapareció
- [ ] La consola del browser está limpia (sin errores ni warnings)
- [ ] `npm test -- --run` sigue pasando (el fix no introdujo regresiones)
- [ ] Si el fix fue en SSE cleanup: React DevTools Profiler confirma una sola instancia de EventSource
