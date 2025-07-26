export declare function multiply(a: number, b: number): number;
export declare function downloadRealmFile(downloadUrl: string, destinationPath: string): Promise<{
    filePath: string;
    fileSize: number;
    wasCompressed?: boolean;
    compressedSize?: number;
}>;
export declare function syncData(syncConfig: {
    collections: Array<{
        name: string;
    }>;
}): Promise<{
    status: string;
    syncedCollections: number;
}>;
export declare function validateLocalFiles(filePaths: string[]): Promise<{
    files: Array<{
        path: string;
        exists: boolean;
        size: number;
    }>;
    validFiles: number;
    invalidFiles: number;
}>;
//# sourceMappingURL=index.d.ts.map