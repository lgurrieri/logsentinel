import { useId, useState } from 'react';
import type { ChangeEvent, FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { KNOWN_SYSTEMS, RAW_LOG_MAX_LENGTH, URGENCY_OPTIONS } from '../constants/incidentFormOptions';
import { validateIncidentForm } from '../validation/validateIncidentForm';
import { createIncident, IncidentApiError } from '../api/incidentsApi';
import type { IncidentFormData, IncidentFormErrors, IncidentUiState } from '../types/incident.types';

const INITIAL_FORM_DATA: IncidentFormData = {
  systemName: '',
  urgency: '',
  rawLogSnapshot: '',
};

const GENERIC_VALIDATION_MESSAGE = 'Los datos ingresados no son válidos. Revisá el formulario e intentá nuevamente.';
const GENERIC_SERVER_MESSAGE = 'Ocurrió un error en el servidor. Intentá nuevamente más tarde.';

function serverErrorMessageFor(error: unknown): string {
  if (error instanceof IncidentApiError && error.status === 400) {
    return GENERIC_VALIDATION_MESSAGE;
  }
  return GENERIC_SERVER_MESSAGE;
}

export function IncidentReportForm() {
  const navigate = useNavigate();
  const systemNameId = useId();
  const rawLogSnapshotId = useId();

  const [formData, setFormData] = useState<IncidentFormData>(INITIAL_FORM_DATA);
  const [formErrors, setFormErrors] = useState<IncidentFormErrors>({});
  const [uiState, setUiState] = useState<IncidentUiState>('IDLE');
  const [serverErrorMessage, setServerErrorMessage] = useState<string | null>(null);
  const [logTruncated, setLogTruncated] = useState(false);

  const isSubmitting = uiState === 'SUBMITTING';

  function handleSystemNameChange(event: ChangeEvent<HTMLSelectElement>) {
    setFormData((prev) => ({ ...prev, systemName: event.target.value }));
  }

  function handleUrgencyChange(event: ChangeEvent<HTMLInputElement>) {
    const { value } = event.target;
    setFormData((prev) => ({ ...prev, urgency: value as IncidentFormData['urgency'] }));
  }

  function handleRawLogChange(event: ChangeEvent<HTMLTextAreaElement>) {
    const { value } = event.target;
    if (value.length > RAW_LOG_MAX_LENGTH) {
      setFormData((prev) => ({ ...prev, rawLogSnapshot: value.slice(0, RAW_LOG_MAX_LENGTH) }));
      setLogTruncated(true);
    } else {
      setFormData((prev) => ({ ...prev, rawLogSnapshot: value }));
      setLogTruncated(false);
    }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    const validationErrors = validateIncidentForm(formData);
    setFormErrors(validationErrors);
    if (Object.keys(validationErrors).length > 0) {
      return;
    }

    setServerErrorMessage(null);
    setUiState('SUBMITTING');

    try {
      const incident = await createIncident({
        systemName: formData.systemName,
        urgency: formData.urgency as Exclude<IncidentFormData['urgency'], ''>,
        rawLogSnapshot: formData.rawLogSnapshot,
      });
      setUiState('SUCCESS');
      navigate(`/incidents/${incident.id}/dashboard`);
    } catch (error) {
      setServerErrorMessage(serverErrorMessageFor(error));
      setUiState('SERVER_ERROR');
    }
  }

  return (
    <form
      onSubmit={handleSubmit}
      noValidate
      className="flex flex-col gap-6 p-6 bg-zinc-900 border border-zinc-700 rounded-lg"
    >
      {uiState === 'SERVER_ERROR' && serverErrorMessage && (
        <div
          role="alert"
          className="p-4 rounded-lg border border-red-500 bg-zinc-900 text-red-400"
        >
          {serverErrorMessage}
        </div>
      )}

      <div className="flex flex-col gap-2">
        <label htmlFor={systemNameId} className="text-zinc-100">
          Sistema
        </label>
        <select
          id={systemNameId}
          value={formData.systemName}
          onChange={handleSystemNameChange}
          disabled={isSubmitting}
          className="bg-zinc-950 text-zinc-100 border border-zinc-700 rounded-lg p-2"
        >
          <option value="">Selecciona un sistema</option>
          {KNOWN_SYSTEMS.map((systemName) => (
            <option key={systemName} value={systemName}>
              {systemName}
            </option>
          ))}
        </select>
        {formErrors.systemName && <p className="text-red-400">{formErrors.systemName}</p>}
      </div>

      <fieldset className="flex flex-col gap-2" disabled={isSubmitting}>
        <legend className="text-zinc-100">Urgencia</legend>
        <div className="flex gap-4 flex-wrap">
          {URGENCY_OPTIONS.map((option) => (
            <label key={option.value} className={`flex items-center gap-2 border rounded-lg p-2 ${option.colorClass}`}>
              <input
                type="radio"
                name="urgency"
                value={option.value}
                checked={formData.urgency === option.value}
                onChange={handleUrgencyChange}
                disabled={isSubmitting}
              />
              {option.label}
            </label>
          ))}
        </div>
        {formErrors.urgency && <p className="text-red-400">{formErrors.urgency}</p>}
      </fieldset>

      <div className="flex flex-col gap-2">
        <label htmlFor={rawLogSnapshotId} className="text-zinc-100">
          Volcado de logs
        </label>
        <textarea
          id={rawLogSnapshotId}
          value={formData.rawLogSnapshot}
          onChange={handleRawLogChange}
          disabled={isSubmitting}
          rows={12}
          className="font-mono text-sm bg-zinc-950 text-green-400 border border-zinc-700 rounded-lg p-2"
        />
        <div className="flex justify-between text-zinc-400 text-sm">
          <span>
            {logTruncated && (
              <span className="text-amber-400">
                Se alcanzó el límite de 100.000 caracteres; el texto fue truncado.
              </span>
            )}
          </span>
          <span>
            {formData.rawLogSnapshot.length} / {RAW_LOG_MAX_LENGTH}
          </span>
        </div>
        {formErrors.rawLogSnapshot && <p className="text-red-400">{formErrors.rawLogSnapshot}</p>}
      </div>

      <button
        type="submit"
        disabled={isSubmitting}
        className="bg-blue-400 text-zinc-950 font-semibold rounded-lg p-2 disabled:opacity-50"
      >
        {isSubmitting ? 'Enviando…' : 'Reportar incidente'}
      </button>
    </form>
  );
}
