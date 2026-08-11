import { marked } from 'marked';
import DOMPurify from 'dompurify';

marked.setOptions({ gfm: true, breaks: true });

/**
 * Convierte un string Markdown (emitido token a token por el LLM vía SSE) en HTML
 * seguro para insertar en el DOM.
 *
 * El texto del stream es untrusted input (LOG-US3-FE-03): `marked` únicamente parsea
 * sintaxis Markdown a HTML — no sanea nada. `DOMPurify.sanitize()` es quien garantiza
 * que no queden `<script>`, manejadores `on*` ni URIs `javascript:` antes de que el
 * HTML resultante se use en `dangerouslySetInnerHTML` (verify-clean-arch Check 8). Este
 * módulo es el único punto del código donde se llama `marked` + `DOMPurify` — cualquier
 * otro uso de `dangerouslySetInnerHTML` en el frontend debe pasar por aquí.
 */
export function sanitizeMarkdown(markdownText: string): string {
  if (!markdownText) return '';

  const rawHtml = marked.parse(markdownText, { async: false });
  return DOMPurify.sanitize(rawHtml);
}
