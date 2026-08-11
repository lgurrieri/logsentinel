import { describe, it, expect } from 'vitest';
import { tokenizeScript, tokenizeScriptLine } from './highlightScript';

describe('tokenizeScriptLine', () => {
  it('trata una línea que comienza con # como un único token de tipo comment', () => {
    const tokens = tokenizeScriptLine('# reinicia el servicio afectado');
    expect(tokens).toEqual([{ text: '# reinicia el servicio afectado', kind: 'comment' }]);
  });

  it('trata una línea con # tras espacios iniciales como comment', () => {
    const tokens = tokenizeScriptLine('   # comentario indentado');
    expect(tokens).toEqual([{ text: '   # comentario indentado', kind: 'comment' }]);
  });

  it('una línea sin comillas ni comentario es un único token plain', () => {
    const tokens = tokenizeScriptLine('systemctl restart payment-gw');
    expect(tokens).toEqual([{ text: 'systemctl restart payment-gw', kind: 'plain' }]);
  });

  it('reconoce substrings entre comillas dobles como token string', () => {
    const tokens = tokenizeScriptLine('echo "hola mundo"');
    expect(tokens).toEqual([
      { text: 'echo ', kind: 'plain' },
      { text: '"hola mundo"', kind: 'string' },
    ]);
  });

  it('reconoce substrings entre comillas simples como token string', () => {
    const tokens = tokenizeScriptLine("echo 'hola mundo'");
    expect(tokens).toEqual([
      { text: 'echo ', kind: 'plain' },
      { text: "'hola mundo'", kind: 'string' },
    ]);
  });

  it('reconoce múltiples strings en la misma línea', () => {
    const tokens = tokenizeScriptLine('cp "origen.txt" "destino.txt"');
    expect(tokens).toEqual([
      { text: 'cp ', kind: 'plain' },
      { text: '"origen.txt"', kind: 'string' },
      { text: ' ', kind: 'plain' },
      { text: '"destino.txt"', kind: 'string' },
    ]);
  });

  it('una línea vacía retorna un único token plain vacío', () => {
    expect(tokenizeScriptLine('')).toEqual([{ text: '', kind: 'plain' }]);
  });
});

describe('tokenizeScript', () => {
  it('divide el script en líneas y tokeniza cada una', () => {
    const script = "#!/bin/bash\n# restart the service\nsystemctl restart payment-gw";
    const lines = tokenizeScript(script);

    expect(lines).toHaveLength(3);
    expect(lines[0]).toEqual([{ text: '#!/bin/bash', kind: 'comment' }]);
    expect(lines[1]).toEqual([{ text: '# restart the service', kind: 'comment' }]);
    expect(lines[2]).toEqual([{ text: 'systemctl restart payment-gw', kind: 'plain' }]);
  });
});
