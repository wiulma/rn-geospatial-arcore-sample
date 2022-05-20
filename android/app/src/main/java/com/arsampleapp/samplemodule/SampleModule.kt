package com.arsampleapp.samplemodule

import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class SampleModule(context: ReactApplicationContext): ReactContextBaseJavaModule() {

    private val _mainHandler = Handler(Looper.getMainLooper())
    private val rContext: ReactApplicationContext = context
    var secondsCount = 0

    override fun getName(): String {
        return "Clock"
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @ReactMethod
    fun getCurrentTime(promise: Promise) {
        val date = ZonedDateTime.now( ZoneOffset.UTC ).format( DateTimeFormatter.ISO_INSTANT )
        promise.resolve(date)
    }

    @ReactMethod
    fun dispatchEventEverySecond() {
        _mainHandler.post(object : Runnable {
            override fun run() {
                secondsCount += 1
                val event = Arguments.createMap()
                event.putInt("count", secondsCount)
                sendEvent(
                        rContext,
                        "onTimeUpdated",
                        event
                )
                _mainHandler.postDelayed(this, 1000)
            }
        })
    }

    private fun sendEvent(
            reactContext: ReactContext,
            eventName: String,
            params: WritableMap
    ) {
        reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit(eventName, params)
    }


    @ReactMethod
    fun addListener(eventName: String?) {
        // Keep: Required for RN built in Event Emitter Calls.
    }
    @ReactMethod
    fun removeListeners(count: Int?) {
        // Keep: Required for RN built in Event Emitter Calls.
    }
}