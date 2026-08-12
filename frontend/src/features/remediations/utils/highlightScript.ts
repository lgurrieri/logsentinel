/**
 * Tokenizador mínimo de sintaxis para el `CodeBlock` de la Caja de Código Estática
 * (LOG-US4-FE-03). No es un parser de Bash completo: distingue tres categorías
 * suficientes para dar una señal visual de "esto es código, no texto plano" sin sumar
 * una dependencia de resaltado de sintaxis de terceros (KISS/YAGNI) —
 * `generatedScript`/`suggestedScript` en la práctica son scripts Bash cortos generados
 * por la IA (ver ejemplos en `docs/openapi: 3.0.yml`), no necesitan un lexer completo.
 *
 * - Líneas que comienzan con `#` (comentarios / shebang) → `comment`.
 * - Substrings entre comillas simples o dobles → `string`.
 * - Todo lo demás → `plain`.
 */

export type ScriptTokenKind = 'plain' | 'comment' | 'string';

export interface ScriptToken {
  text: string;
  kind: ScriptTokenKind;
}

const QUOTED_STRING_PATTERN = /("[^"]*"|'[^']*')/g;

export function tokenizeScriptLine(line: string): ScriptToken[] {
  if (/^\s*#/.test(line)) {
    return [{ text: line, kind: 'comment' }];
  }

  const tokens: ScriptToken[] = [];
  let lastIndex = 0;
  let match: RegExpExecArray | null;

  QUOTED_STRING_PATTERN.lastIndex = 0;
  while ((match = QUOTED_STRING_PATTERN.exec(line)) !== null) {
    if (match.index > lastIndex) {
      tokens.push({ text: line.slice(lastIndex, match.index), kind: 'plain' });
    }
    tokens.push({ text: match[0], kind: 'string' });
    lastIndex = QUOTED_STRING_PATTERN.lastIndex;
  }

  if (lastIndex < line.length) {
    tokens.push({ text: line.slice(lastIndex), kind: 'plain' });
  }

  if (tokens.length === 0) {
    tokens.push({ text: line, kind: 'plain' });
  }

  return tokens;
}

export function tokenizeScript(script: string): ScriptToken[][] {
  return script.split('\n').map(tokenizeScriptLine);
}
