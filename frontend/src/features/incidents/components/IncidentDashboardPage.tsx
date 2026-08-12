import { useParams } from 'react-router-dom';
import { DiagnosticStreamProvider } from '../context/DiagnosticStreamContext';
import { DiagnosticTerminal } from './DiagnosticTerminal';
import { useIncidentDetail } from '../hooks/useIncidentDetail';
import { RemediationProvider, RemediationPanel } from '@/features/remediations';

export function IncidentDashboardPage() {
  const { id } = useParams<{ id: string }>();
  const incidentId = id ?? null;
  const { suggestedScript, error } = useIncidentDetail(incidentId);

  return (
    <main className="min-h-screen bg-zinc-950 text-zinc-100 p-8 flex flex-col items-center gap-4">
      <h1 className="text-2xl font-mono text-green-400">LogSentinel</h1>
      <p className="text-zinc-300">
        Incidente <span className="font-mono text-green-400">{id}</span> recibido. Diagnóstico de IA en curso:
      </p>
      <div className="w-full max-w-3xl">
        <DiagnosticStreamProvider>
          <DiagnosticTerminal incidentId={incidentId} />
        </DiagnosticStreamProvider>
      </div>

      {incidentId && (
        <div className="w-full max-w-3xl flex flex-col gap-4">
          <h2 className="text-lg font-mono text-zinc-100">Remediación sugerida</h2>
          {error && (
            <p role="alert" className="text-red-400 text-sm">
              {error}
            </p>
          )}
          <RemediationProvider>
            <RemediationPanel incidentId={incidentId} generatedScript={suggestedScript} />
          </RemediationProvider>
        </div>
      )}
    </main>
  );
}

