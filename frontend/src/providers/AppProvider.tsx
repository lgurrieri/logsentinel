import type { ReactNode } from 'react';
import { UIProvider } from '@/contexts/UIContext';

export function AppProvider({ children }: { children: ReactNode }) {
  return <UIProvider>{children}</UIProvider>;
}
