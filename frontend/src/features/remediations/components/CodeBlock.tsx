import { useEffect, useRef, useState } from 'react';
import { tokenizeScript } from '../utils/highlightScript';
import type { ScriptTokenKind } from '../utils/highlightScript';

interface CodeBlockProps {
  /** Script Bash/SQL de solo lectura (`IncidentAnalysis.suggestedScript`). */
  code: string;
}

const COPY_CONFIRMATION_MS = 2000;

const TOKEN_CLASS: Record<ScriptTokenKind, string> = {
  plain: 'text-zinc-100',
  comment: 'text-zinc-500',
  string: 'text-green-400',
};

/**
 * Caja de Código Estática (LOG-US4-FE-03): presenta `generatedScript`/`suggestedScript`
 * en un bloque de solo lectura con formato monoespaciado y resaltado de sintaxis
 * mínimo (`tokenizeScript` — comentarios/strings, ver nota de diseño ahí), más un botón
 * de "Copiar al portapapeles" que confirma visualmente con un check temporal.
 */
export function CodeBlock({ code }: CodeBlockProps) {
  const [copied, setCopied] = useState(false);
  const timeoutRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

  useEffect(() => {
    return () => {
      if (timeoutRef.current) clearTimeout(timeoutRef.current);
    };
  }, []);

  async function handleCopy() {
    await navigator.clipboard.writeText(code);
    setCopied(true);
    if (timeoutRef.current) clearTimeout(timeoutRef.current);
    timeoutRef.current = setTimeout(() => setCopied(false), COPY_CONFIRMATION_MS);
  }

  const lines = tokenizeScript(code);

  return (
    <div className="flex flex-col bg-zinc-950 border border-zinc-700 rounded-lg overflow-hidden">
      <div className="flex justify-end px-2 py-2 bg-zinc-900 border-b border-zinc-700">
        <button
          type="button"
          onClick={handleCopy}
          className="text-xs font-semibold text-zinc-100 bg-zinc-700 rounded-lg px-3 py-2"
        >
          {copied ? '✓ Copiado' : 'Copiar al portapapeles'}
        </button>
      </div>
      <pre
        data-testid="remediation-code-block"
        aria-label="Script de remediación sugerido (solo lectura)"
        className="font-mono text-sm p-4 overflow-x-auto whitespace-pre"
      >
        <code>
          {lines.map((tokens, lineIndex) => (
            <div key={lineIndex}>
              {tokens.map((token, tokenIndex) => (
                <span key={tokenIndex} className={TOKEN_CLASS[token.kind]}>
                  {token.text}
                </span>
              ))}
            </div>
          ))}
        </code>
      </pre>
    </div>
  );
}
