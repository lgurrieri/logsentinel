import { useContext } from 'react';
import { RemediationContext } from '../context/RemediationContext';

// Los componentes NUNCA importan useContext directamente.
// Este hook es el único punto de acceso al contexto de la feature.
export function useRemediation() {
  const context = useContext(RemediationContext);
  if (!context) {
    throw new Error('useRemediation debe usarse dentro de RemediationProvider');
  }
  return context;
}
