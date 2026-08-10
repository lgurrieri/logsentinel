import { describe, it, expect } from 'vitest';
import { validateIncidentForm } from './validateIncidentForm';
import type { IncidentFormData } from '../types/incident.types';

describe('validateIncidentForm', () => {
  const validData: IncidentFormData = {
    systemName: 'payment-gateway',
    urgency: 'CRITICAL',
    rawLogSnapshot: 'ERROR: pool exhausted',
  };

  it('no reporta errores cuando todos los campos son válidos', () => {
    expect(validateIncidentForm(validData)).toEqual({});
  });

  it('exige systemName cuando está vacío', () => {
    const errors = validateIncidentForm({ ...validData, systemName: '' });
    expect(errors.systemName).toBeDefined();
  });

  it('exige urgency cuando no fue seleccionada', () => {
    const errors = validateIncidentForm({ ...validData, urgency: '' });
    expect(errors.urgency).toBeDefined();
  });

  it('exige rawLogSnapshot cuando está vacío', () => {
    const errors = validateIncidentForm({ ...validData, rawLogSnapshot: '' });
    expect(errors.rawLogSnapshot).toBeDefined();
  });

  it('exige rawLogSnapshot cuando contiene solo espacios en blanco', () => {
    const errors = validateIncidentForm({ ...validData, rawLogSnapshot: '          ' });
    expect(errors.rawLogSnapshot).toBeDefined();
  });

  it('rechaza rawLogSnapshot por debajo del mínimo de 10 caracteres que exige el backend', () => {
    const errors = validateIncidentForm({ ...validData, rawLogSnapshot: 'short' });
    expect(errors.rawLogSnapshot).toBeDefined();
  });

  it('acepta rawLogSnapshot con exactamente 10 caracteres', () => {
    const errors = validateIncidentForm({ ...validData, rawLogSnapshot: '0123456789' });
    expect(errors.rawLogSnapshot).toBeUndefined();
  });
});
