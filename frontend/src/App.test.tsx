import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect } from 'vitest';
import App from './App';

describe('App routing', () => {
  it('resuelve /incidents/{id}/dashboard tras el redirect de éxito del formulario', () => {
    render(
      <MemoryRouter initialEntries={['/incidents/inc-42/dashboard']}>
        <App />
      </MemoryRouter>,
    );

    expect(screen.getByText(/inc-42/i)).toBeInTheDocument();
  });
});
