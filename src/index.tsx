import HybridSync from './NativeHybridSync';

export function multiply(a: number, b: number): number {
  return HybridSync.multiply(a, b);
}

export function downloadRealmFile(
  downloadUrl: string,
  destinationPath: string
) {
  return HybridSync.downloadRealmFile(downloadUrl, destinationPath);
}

export function syncData(syncConfig: { collections: Array<{ name: string }> }) {
  return HybridSync.syncData(syncConfig);
}

export function validateLocalFiles(filePaths: string[]) {
  return HybridSync.validateLocalFiles(filePaths);
}
