import HybridSync from './NativeHybridSync';

export function multiply(a: number, b: number): Promise<number> {
  return HybridSync.multiply(a, b);
}
