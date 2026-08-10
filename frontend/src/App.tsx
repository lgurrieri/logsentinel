import { Route, Routes } from 'react-router-dom';
import { IncidentReportForm, IncidentDashboardPage } from '@/features/incidents';

function IncidentReportPage() {
  return (
    <main className="min-h-screen bg-zinc-950 text-zinc-100 p-8 flex flex-col items-center gap-8">
      <h1 className="text-2xl font-mono text-green-400">LogSentinel</h1>
      <div className="w-full max-w-2xl">
        <IncidentReportForm />
      </div>
    </main>
  );
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<IncidentReportPage />} />
      <Route path="/incidents/:id/dashboard" element={<IncidentDashboardPage />} />
    </Routes>
  );
}
