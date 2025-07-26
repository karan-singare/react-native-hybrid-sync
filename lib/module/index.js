"use strict";

import HybridSync from "./NativeHybridSync.js";
export function multiply(a, b) {
  return HybridSync.multiply(a, b);
}
export function downloadRealmFile(downloadUrl, destinationPath) {
  return HybridSync.downloadRealmFile(downloadUrl, destinationPath);
}
export function syncData(syncConfig) {
  return HybridSync.syncData(syncConfig);
}
export function validateLocalFiles(filePaths) {
  return HybridSync.validateLocalFiles(filePaths);
}
//# sourceMappingURL=index.js.map