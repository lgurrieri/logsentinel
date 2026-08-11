import { describe, it, expect } from 'vitest';
import { sanitizeMarkdown } from './sanitizeMarkdown';

describe('sanitizeMarkdown', () => {
  it('convierte sintaxis Markdown básica a HTML', () => {
    const html = sanitizeMarkdown('**RootCause:** timeout en `payment-db`');

    expect(html).toContain('<strong>RootCause:</strong>');
    expect(html).toContain('<code>payment-db</code>');
  });

  it('elimina etiquetas <script> incrustadas en el texto del LLM', () => {
    const html = sanitizeMarkdown('Diagnóstico<script>alert("xss")</script> completo');

    expect(html).not.toContain('<script');
    expect(html).not.toContain('alert(');
  });

  it('elimina manejadores de eventos inline (onerror, onclick, etc.)', () => {
    const html = sanitizeMarkdown('<img src="x" onerror="alert(1)">');

    expect(html).not.toContain('onerror');
  });

  it('elimina URIs javascript: en enlaces', () => {
    const html = sanitizeMarkdown('[click aquí](javascript:alert(1))');

    expect(html.toLowerCase()).not.toContain('javascript:');
  });

  it('retorna string vacío para un buffer vacío', () => {
    expect(sanitizeMarkdown('')).toBe('');
  });
});
