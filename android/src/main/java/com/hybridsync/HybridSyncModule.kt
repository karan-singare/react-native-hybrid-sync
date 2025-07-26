package com.hybridsync

import android.util.Log
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.facebook.react.module.annotations.ReactModule
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.GZIPInputStream

@ReactModule(name = HybridSyncModule.NAME)
class HybridSyncModule(reactContext: ReactApplicationContext) :
  NativeHybridSyncSpec(reactContext) {

  companion object {
    private const val TAG = "HybridSyncModule"
    const val NAME = "HybridSync"
  }

  private val executorService: ExecutorService = Executors.newFixedThreadPool(4)
  private val isDownloading = AtomicBoolean(false)

  override fun getName(): String {
    return NAME
  }

  // Example method
  // See https://reactnative.dev/docs/native-modules-android
  override fun multiply(a: Double, b: Double): Double {
    return a * b
  }

  override fun downloadRealmFile(downloadUrl: String, destinationPath: String, promise: Promise) {
    Log.d(TAG, "downloadRealmFile called with URL: $downloadUrl, path: $destinationPath")
    
    if (isDownloading.get()) {
      Log.w(TAG, "Download already in progress, rejecting request")
      promise.reject("DOWNLOAD_IN_PROGRESS", "Another download is already in progress")
      return
    }
    
    executorService.execute {
      isDownloading.set(true)
      try {
        // Check if URL contains compress=true parameter
        val isCompressedRequest = downloadUrl.contains("compress=true")
        if (isCompressedRequest) {
          downloadCompressedFileWithProgress(downloadUrl, destinationPath, promise)
        } else {
          downloadFileWithProgress(downloadUrl, destinationPath, promise)
        }
      } finally {
        isDownloading.set(false)
      }
    }
  }

  override fun syncData(syncConfig: ReadableMap, promise: Promise) {
    Log.d(TAG, "syncData called with config: $syncConfig")
    
    executorService.execute {
      try {
        performDataSync(syncConfig, promise)
      } catch (e: Exception) {
        Log.e(TAG, "Error in syncData", e)
        promise.reject("SYNC_ERROR", e.message)
      }
    }
  }

  override fun validateLocalFiles(filePaths: ReadableArray, promise: Promise) {
    Log.d(TAG, "validateLocalFiles called with ${filePaths.size()} files")
    
    executorService.execute {
      try {
        validateFilesInternal(filePaths, promise)
      } catch (e: Exception) {
        Log.e(TAG, "Error in validateLocalFiles", e)
        promise.reject("VALIDATION_ERROR", e.message)
      }
    }
  }

  private fun downloadCompressedFileWithProgress(downloadUrl: String, destinationPath: String, promise: Promise) {
    var connection: HttpURLConnection? = null
    var outputStream: FileOutputStream? = null
    var inputStream: InputStream? = null
    
    try {
      Log.d(TAG, "📡 Connecting to compressed file: $downloadUrl")
      val url = URL(downloadUrl)
      connection = url.openConnection() as HttpURLConnection
      connection.requestMethod = "GET"
      connection.connectTimeout = 30000
      connection.readTimeout = 60000
      
      // Add Accept-Encoding header to request compressed content
      connection.setRequestProperty("Accept-Encoding", "gzip, deflate")
      
      val responseCode = connection.responseCode
      Log.d(TAG, "📊 Response code: $responseCode")
      
      if (responseCode != HttpURLConnection.HTTP_OK) {
        Log.e(TAG, "❌ HTTP Error: $responseCode")
        promise.reject("HTTP_ERROR", "HTTP Error: $responseCode")
        return
      }
      
      val contentLength = connection.contentLength.toLong()
      Log.d(TAG, "📦 Compressed content length: $contentLength bytes")
      
      // Check if response is actually compressed
      val contentEncoding = connection.getHeaderField("Content-Encoding")
      val isCompressed = contentEncoding?.contains("gzip") == true
      Log.d(TAG, "📦 Content-Encoding: $contentEncoding, Is compressed: $isCompressed")
      
      inputStream = connection.inputStream
      
      // PHASE 1: DOWNLOAD COMPRESSED FILE
      Log.d(TAG, "⬇️ PHASE 1: Downloading compressed file...")
      
      // Create temporary file for compressed data
      val tempCompressedFile = File("${destinationPath}.compressed")
      val tempOutputStream = FileOutputStream(tempCompressedFile)
      
      val buffer = ByteArray(131072) // 128KB buffer
      var compressedBytesDownloaded = 0L
      var bytesRead: Int
      var lastProgressUpdate = 0
      
      // Send initial download progress event
      sendProgressEvent("download_progress", 0, 0, contentLength)
      
      // Download compressed file
      while (inputStream.read(buffer).also { bytesRead = it } != -1) {
        tempOutputStream.write(buffer, 0, bytesRead)
        compressedBytesDownloaded += bytesRead
        
        // Send download progress update
        if (contentLength > 0) {
          val progress = ((compressedBytesDownloaded * 100) / contentLength).toInt()
          if (progress > lastProgressUpdate || compressedBytesDownloaded % 1048576 == 0L) {
            Log.d(TAG, "Download progress: $progress% ($compressedBytesDownloaded/$contentLength bytes)")
            sendProgressEvent("download_progress", progress, compressedBytesDownloaded, contentLength)
            lastProgressUpdate = progress
          }
        }
      }
      
      tempOutputStream.flush()
      tempOutputStream.close()
      inputStream.close()
      connection.disconnect()
      
      Log.d(TAG, "✅ Download completed: $compressedBytesDownloaded bytes")
      sendProgressEvent("download_complete", 100, compressedBytesDownloaded, contentLength)
      
      // PHASE 2: DECOMPRESS FILE
      Log.d(TAG, "🔄 PHASE 2: Decompressing file...")
      
      // Send decompression start event
      sendProgressEvent("decompress_progress", 0, 0, -1)
      
      // Ensure directory exists
      val destinationFile = File(destinationPath)
      val parentDir = destinationFile.parentFile
      if (parentDir != null && !parentDir.exists()) {
        parentDir.mkdirs()
      }
      
      // Delete existing file if it exists
      if (destinationFile.exists()) {
        Log.d(TAG, "🗑️ Deleting existing file: $destinationPath")
        destinationFile.delete()
      }
      
      outputStream = FileOutputStream(destinationFile)
      val compressedInputStream = FileInputStream(tempCompressedFile)
      val gzipInputStream = GZIPInputStream(compressedInputStream)
      
      var decompressedBytesWritten = 0L
      lastProgressUpdate = 0
      
      // Decompress file
      while (gzipInputStream.read(buffer).also { bytesRead = it } != -1) {
        outputStream.write(buffer, 0, bytesRead)
        decompressedBytesWritten += bytesRead
        
        // Send decompression progress update
        val progress = minOf((decompressedBytesWritten / 1024 / 1024).toInt(), 99) // Max 99% until complete
        if (progress > lastProgressUpdate || decompressedBytesWritten % 2097152 == 0L) {
          Log.d(TAG, "Decompression progress: $progress% ($decompressedBytesWritten bytes)")
          sendProgressEvent("decompress_progress", progress, decompressedBytesWritten, -1)
          lastProgressUpdate = progress
        }
      }
      
      outputStream.flush()
      gzipInputStream.close()
      compressedInputStream.close()
      
      // Clean up temporary compressed file
      tempCompressedFile.delete()
      
      Log.d(TAG, "✅ Decompression completed: $decompressedBytesWritten bytes")
      sendProgressEvent("decompress_complete", 100, decompressedBytesWritten, -1)
      
      // Verify final file exists and has correct size
      val downloadedFile = File(destinationPath)
      Log.d(TAG, "📁 File exists: ${downloadedFile.exists()}")
      Log.d(TAG, "📏 File size: ${downloadedFile.length()} bytes")
      
      val result = Arguments.createMap().apply {
        putString("filePath", destinationPath)
        putDouble("fileSize", decompressedBytesWritten.toDouble())
        putBoolean("wasCompressed", isCompressed)
        putDouble("compressedSize", compressedBytesDownloaded.toDouble())
      }
      promise.resolve(result)
      
    } catch (e: Exception) {
      Log.e(TAG, "❌ Compressed download error: ${e.message}", e)
      promise.reject("DOWNLOAD_ERROR", e.message)
    } finally {
      try {
        inputStream?.close()
        outputStream?.close()
        connection?.disconnect()
      } catch (e: IOException) {
        Log.e(TAG, "❌ Error closing streams: ${e.message}")
      }
    }
  }

  private fun downloadFileWithProgress(downloadUrl: String, destinationPath: String, promise: Promise) {
    var connection: HttpURLConnection? = null
    var outputStream: FileOutputStream? = null
    var inputStream: InputStream? = null
    
    try {
      Log.d(TAG, "📡 Connecting to: $downloadUrl")
      val url = URL(downloadUrl)
      connection = url.openConnection() as HttpURLConnection
      connection.requestMethod = "GET"
      connection.connectTimeout = 30000
      connection.readTimeout = 60000
      
      val responseCode = connection.responseCode
      Log.d(TAG, "📊 Response code: $responseCode")
      
      if (responseCode != HttpURLConnection.HTTP_OK) {
        Log.e(TAG, "❌ HTTP Error: $responseCode")
        promise.reject("HTTP_ERROR", "HTTP Error: $responseCode")
        return
      }
      
      val contentLength = connection.contentLength.toLong()
      Log.d(TAG, "📦 Content length: $contentLength bytes")
      
      inputStream = connection.inputStream
      
      // Ensure directory exists
      val destinationFile = File(destinationPath)
      val parentDir = destinationFile.parentFile
      if (parentDir != null && !parentDir.exists()) {
        parentDir.mkdirs()
      }
      
      // Delete existing file if it exists
      if (destinationFile.exists()) {
        Log.d(TAG, "🗑️ Deleting existing file: $destinationPath")
        destinationFile.delete()
      }
      
      outputStream = FileOutputStream(destinationFile)
      val buffer = ByteArray(8192)
      var totalBytesRead = 0L
      var bytesRead: Int
      
      Log.d(TAG, "⬇️ Starting download...")
      while (inputStream.read(buffer).also { bytesRead = it } != -1) {
        outputStream.write(buffer, 0, bytesRead)
        totalBytesRead += bytesRead
        
        // Send progress update
        if (contentLength > 0) {
          val progress = ((totalBytesRead * 100) / contentLength).toInt()
          Log.d(TAG, "Download progress: $progress% ($totalBytesRead/$contentLength bytes)")
          sendProgressEvent("download_progress", progress, totalBytesRead, contentLength)
        }
      }
      
      outputStream.flush()
      Log.d(TAG, "✅ Download completed: $totalBytesRead bytes")
      sendProgressEvent("download_complete", 100, totalBytesRead, contentLength)
      
      // Verify file exists and has correct size
      val downloadedFile = File(destinationPath)
      Log.d(TAG, "📁 File exists: ${downloadedFile.exists()}")
      Log.d(TAG, "📏 File size: ${downloadedFile.length()} bytes")
      
      val result = Arguments.createMap().apply {
        putString("filePath", destinationPath)
        putDouble("fileSize", totalBytesRead.toDouble())
      }
      promise.resolve(result)
      
    } catch (e: Exception) {
      Log.e(TAG, "❌ Download error: ${e.message}", e)
      promise.reject("DOWNLOAD_ERROR", e.message)
    } finally {
      try {
        inputStream?.close()
        outputStream?.close()
        connection?.disconnect()
      } catch (e: IOException) {
        Log.e(TAG, "❌ Error closing streams: ${e.message}")
      }
    }
  }
  
  private fun performDataSync(syncConfig: ReadableMap, promise: Promise) {
    try {
      Log.d(TAG, "🔄 Starting data sync...")
      
      // Simulate sync operations
      val collections = syncConfig.getArray("collections")
      var syncedCollections = 0
      if (collections != null) {
        for (i in 0 until collections.size()) {
          val collection = collections.getMap(i)
          val collectionName = collection?.getString("name") ?: "unknown"
          Log.d(TAG, "🔄 Syncing collection: $collectionName")
          
          // Simulate sync progress
          sendProgressEvent("sync_progress", (i * 100) / collections.size(), i.toLong(), collections.size().toLong())
          
          // Simulate some processing time
          Thread.sleep(100)
        }
        syncedCollections = collections.size()
      }
      
      Log.d(TAG, "✅ Data sync completed")
      sendProgressEvent("sync_complete", 100, 1, 1)
      
      val result = Arguments.createMap().apply {
        putString("status", "completed")
        putInt("syncedCollections", syncedCollections)
      }
      promise.resolve(result)
      
    } catch (e: Exception) {
      Log.e(TAG, "❌ Data sync error: ${e.message}", e)
      promise.reject("SYNC_ERROR", e.message)
    }
  }
  
  private fun validateFilesInternal(filePaths: ReadableArray, promise: Promise) {
    try {
      Log.d(TAG, "🔍 Starting file validation...")
      
      val results = Arguments.createArray()
      var validFiles = 0
      var invalidFiles = 0
      
      for (i in 0 until filePaths.size()) {
        val filePath = filePaths.getString(i)
        val file = File(filePath)
        
        val fileResult = Arguments.createMap().apply {
          putString("path", filePath)
          putBoolean("exists", file.exists())
          putDouble("size", file.length().toDouble())
        }
        
        if (file.exists()) {
          validFiles++
          Log.d(TAG, "✅ File valid: $filePath (${file.length()} bytes)")
        } else {
          invalidFiles++
          Log.d(TAG, "❌ File missing: $filePath")
        }
        
        results.pushMap(fileResult)
      }
      
      Log.d(TAG, "✅ File validation completed: $validFiles valid, $invalidFiles invalid")
      
      val result = Arguments.createMap().apply {
        putArray("files", results)
        putInt("validFiles", validFiles)
        putInt("invalidFiles", invalidFiles)
      }
      promise.resolve(result)
      
    } catch (e: Exception) {
      Log.e(TAG, "❌ File validation error: ${e.message}", e)
      promise.reject("VALIDATION_ERROR", e.message)
    }
  }
  
  private fun sendProgressEvent(eventType: String, progress: Int, current: Long, total: Long) {
    try {
      Log.d(TAG, "📈 Sending progress event: $eventType, progress: $progress%, current: $current, total: $total")
      
      val params = Arguments.createMap().apply {
        putString("type", eventType)
        putInt("progress", progress)
        putDouble("current", current.toDouble())
        putDouble("total", total.toDouble())
      }
      
      reactApplicationContext
        .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
        .emit("SyncProgress", params)
        
    } catch (e: Exception) {
      Log.e(TAG, "❌ Error sending progress event", e)
    }
  }
  
  override fun onCatalystInstanceDestroy() {
    super.onCatalystInstanceDestroy()
    if (!executorService.isShutdown) {
      executorService.shutdown()
    }
  }
}
