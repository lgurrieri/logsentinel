import { vi } from 'vitest';

export type MockEventSource = {
  url: string;
  onmessage: ((e: MessageEvent) => void) | null;
  onerror: ((e: Event) => void) | null;
  close: ReturnType<typeof vi.fn>;
};

let instances: MockEventSource[] = [];

export function installMockEventSource() {
  instances = [];
  vi.stubGlobal(
    'EventSource',
    vi.fn().mockImplementation(function (this: MockEventSource, url: string) {
      this.url = url;
      this.onmessage = null;
      this.onerror = null;
      this.close = vi.fn();
      instances.push(this);
    }),
  );
}

export function currentSource(): MockEventSource {
  return instances[instances.length - 1];
}

export function instanceCount(): number {
  return instances.length;
}
