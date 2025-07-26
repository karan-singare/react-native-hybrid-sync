package com.hybridsync

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.Promise

class HybridSyncModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

  override fun getName(): String {
    return NAME
  }

  // Example method
  // See https://reactnative.dev/docs/native-modules-android
  @ReactMethod
  fun multiply(a: Double, b: Double, promise: Promise) {
    try {
      val result = a * b
      promise.resolve(result)
    } catch (e: Exception) {
      promise.reject("ERROR", e.message, e)
    }
  }

  companion object {
    const val NAME = "HybridSync"
  }
}
