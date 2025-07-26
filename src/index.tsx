import HybridSync from './NativeHybridSync';
import { Platform } from 'react-native';

export function multiply(a: number, b: number): number {
  return HybridSync.multiply(a, b);
}

export function downloadRealmFile(
  downloadUrl: string,
  destinationPath?: string
) {
  // If no destination path provided, use a default path in the app's documents directory
  const finalPath = destinationPath || getDefaultDownloadPath();
  return HybridSync.downloadRealmFile(downloadUrl, finalPath);
}

export function syncData(syncConfig: { collections: Array<{ name: string }> }) {
  return HybridSync.syncData(syncConfig);
}

export function validateLocalFiles(filePaths: string[]) {
  return HybridSync.validateLocalFiles(filePaths);
}

function getDefaultDownloadPath(): string {
  if (Platform.OS === 'android') {
    return '/data/data/com.testsyncapp/files/downloaded-file';
  } else {
    return '/tmp/downloaded-file';
  }
}
