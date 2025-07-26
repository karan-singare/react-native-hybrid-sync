"use strict";

import HybridSync from "./NativeHybridSync.js";
import { Platform } from 'react-native';
export function multiply(a, b) {
  return HybridSync.multiply(a, b);
}
export function downloadRealmFile(downloadUrl, destinationPath) {
  // If no destination path provided, use a default path in the app's documents directory
  const finalPath = destinationPath || getDefaultDownloadPath();
  return HybridSync.downloadRealmFile(downloadUrl, finalPath);
}
export function syncData(syncConfig) {
  return HybridSync.syncData(syncConfig);
}
export function validateLocalFiles(filePaths) {
  return HybridSync.validateLocalFiles(filePaths);
}
function getDefaultDownloadPath() {
  if (Platform.OS === 'android') {
    return '/data/data/com.testsyncapp/files/downloaded-file';
  } else {
    return '/tmp/downloaded-file';
  }
}
//# sourceMappingURL=index.js.map