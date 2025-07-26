import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

export interface Spec extends TurboModule {
  multiply(a: number, b: number): number;
  downloadRealmFile(
    downloadUrl: string,
    destinationPath: string
  ): Promise<{
    filePath: string;
    fileSize: number;
    wasCompressed?: boolean;
    compressedSize?: number;
  }>;
  syncData(syncConfig: { collections: Array<{ name: string }> }): Promise<{
    status: string;
    syncedCollections: number;
  }>;
  validateLocalFiles(filePaths: string[]): Promise<{
    files: Array<{
      path: string;
      exists: boolean;
      size: number;
    }>;
    validFiles: number;
    invalidFiles: number;
  }>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('HybridSync');
