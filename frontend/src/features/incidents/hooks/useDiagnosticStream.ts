import { useContext } from 'react';
import { DiagnosticStreamContext } from '../context/DiagnosticStreamContext';

// Los componentes NUNCA importan useContext directamente.
// Este hook es el único punto de acceso al contexto de la feature.
export function useDiagnosticStream() {
  const context = useContext(DiagnosticStreamContext);
  if (!context) {
    throw new Error('useDiagnosticStream debe usarse dentro de DiagnosticStreamProvider');
  }
  return context;
}
