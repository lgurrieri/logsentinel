import { useParams } from 'react-router-dom';

export function IncidentDashboardPage() {
  const { id } = useParams<{ id: string }>();

  return (
    <main className="min-h-screen bg-zinc-950 text-zinc-100 p-8 flex flex-col items-center gap-4">
      <h1 className="text-2xl font-mono text-green-400">LogSentinel</h1>
      <p className="text-zinc-300">
        Incidente <span className="font-mono text-green-400">{id}</span> recibido. El diagnóstico de IA está en preparación.
      </p>
    </main>
  );
}
