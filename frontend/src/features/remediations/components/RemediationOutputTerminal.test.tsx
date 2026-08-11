import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { RemediationOutputTerminal } from './RemediationOutputTerminal';

describe('RemediationOutputTerminal', () => {
  it('no renderiza nada en estado READY (panel inicialmente vacío/ausente)', () => {
    const { container } = render(
      <RemediationOutputTerminal executionStatus="READY" stdoutLog={null} stderrLog={null} />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it('no renderiza nada en estado CONFIRMING', () => {
    const { container } = render(
      <RemediationOutputTerminal executionStatus="CONFIRMING" stdoutLog={null} stderrLog={null} />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it('aparece con un indicador de progreso al arrancar el proceso (EXECUTING)', () => {
    render(<RemediationOutputTerminal executionStatus="EXECUTING" stdoutLog={null} stderrLog={null} />);

    expect(screen.getByRole('region', { name: /monitor de salida/i })).toBeInTheDocument();
    expect(screen.getByText(/ejecutando script/i)).toBeInTheDocument();
  });

  it('renderiza las líneas de stdout en tipografía clara ordinaria', () => {
    render(
      <RemediationOutputTerminal
        executionStatus="EXECUTION_SUCCESS"
        stdoutLog={'línea uno\nlínea dos'}
        stderrLog={''}
      />,
    );

    const stdoutLine = screen.getByText('línea uno');
    expect(stdoutLine.className).toMatch(/text-zinc-300/);
    expect(screen.getByText('línea dos')).toBeInTheDocument();
  });

  it('antepone la etiqueta [ERROR] y pinta en rojo cada línea de stderr', () => {
    render(
      <RemediationOutputTerminal
        executionStatus="EXECUTION_FAILED"
        stdoutLog={''}
        stderrLog={'permission denied\nexit status 1'}
      />,
    );

    const firstErrorLine = screen.getByText('[ERROR] permission denied');
    expect(firstErrorLine.className).toMatch(/text-red-400/);
    expect(screen.getByText('[ERROR] exit status 1')).toBeInTheDocument();
  });

  it('muestra un indicador definitivo de éxito cuando executionStatus es EXECUTION_SUCCESS', () => {
    render(<RemediationOutputTerminal executionStatus="EXECUTION_SUCCESS" stdoutLog="ok" stderrLog="" />);

    expect(screen.getByText(/ejecución exitosa/i)).toBeInTheDocument();
  });

  it('muestra un indicador definitivo de fallo cuando executionStatus es EXECUTION_FAILED', () => {
    render(<RemediationOutputTerminal executionStatus="EXECUTION_FAILED" stdoutLog="" stderrLog="boom" />);

    expect(screen.getByText(/ejecución fallida/i)).toBeInTheDocument();
  });
});
