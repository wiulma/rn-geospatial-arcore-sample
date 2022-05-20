/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.arsampleapp.helloar

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.arsampleapp.BuildConfig
import com.arsampleapp.R
import com.arsampleapp.common.helpers.*
import com.google.ar.core.Config
import com.google.ar.core.Config.InstantPlacementMode
import com.google.ar.core.Session
import com.arsampleapp.common.samplerender.SampleRender
import com.arsampleapp.whileinuselocation.ForegroundOnlyLocationService
import com.arsampleapp.whileinuselocation.SharedPreferenceUtil
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.snackbar.Snackbar
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException

/**
 * This is a simple example that shows how to create an augmented reality (AR) application using the
 * ARCore API. The application will display any detected planes and will allow the user to tap on a
 * plane to place a 3D model.
 */

private const val REQUEST_FOREGROUND_ONLY_PERMISSIONS_REQUEST_CODE = 34

class HelloArActivity : AppCompatActivity() {
  companion object {
    private const val TAG = "HelloArActivity"
  }

  lateinit var arCoreSessionHelper: ARCoreSessionLifecycleHelper
  lateinit var view: HelloArView
  lateinit var renderer: HelloArRenderer
  lateinit var fusedLocationClient: FusedLocationProviderClient

  private lateinit var sharedPreferences: SharedPreferences

  val instantPlacementSettings = InstantPlacementSettings()
  val depthSettings = DepthSettings()
  val foregroundOnlyLocationServiceBound = false

  // Provides location updates for while-in-use feature.
  var foregroundOnlyLocationService: ForegroundOnlyLocationService? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    sharedPreferences =
      getSharedPreferences(getString(R.string.preference_file_key), Context.MODE_PRIVATE)

    this.initLocationService()
    fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    // Setup ARCore session lifecycle helper and configuration.
    arCoreSessionHelper = ARCoreSessionLifecycleHelper(this)
    // If Session creation or Session.resume() fails, display a message and log detailed
    // information.
    arCoreSessionHelper.exceptionCallback =
      { exception ->
        val message =
          when (exception) {
            is UnavailableUserDeclinedInstallationException ->
              "Please install Google Play Services for AR"
            is UnavailableApkTooOldException -> "Please update ARCore"
            is UnavailableSdkTooOldException -> "Please update this app"
            is UnavailableDeviceNotCompatibleException -> "This device does not support AR"
            is CameraNotAvailableException -> "Camera not available. Try restarting the app."
            else -> "Failed to create AR session: $exception"
          }
        Log.e(TAG, "ARCore threw an exception", exception)
        view.snackbarHelper.showError(this, message)
      }

    // Configure session features, including: Lighting Estimation, Depth mode, Instant Placement.
    arCoreSessionHelper.beforeSessionResume = ::configureSession
    lifecycle.addObserver(arCoreSessionHelper)

    // Set up the Hello AR renderer.
    renderer = HelloArRenderer(this)
    lifecycle.addObserver(renderer)

    // Set up Hello AR UI.
    view = HelloArView(this)
    lifecycle.addObserver(view)
    setContentView(view.root)

    // Sets up an example renderer using our HelloARRenderer.
    SampleRender(view.surfaceView, renderer, assets)

    depthSettings.onCreate(this)
    instantPlacementSettings.onCreate(this)
  }

  fun initLocationService() {
    val enabled = sharedPreferences.getBoolean(SharedPreferenceUtil.KEY_FOREGROUND_ENABLED, false)

    if (enabled) {
      foregroundOnlyLocationService?.unsubscribeToLocationUpdates()
    } else {
      // TODO: Step 1.0, Review Permissions: Checks and requests if needed.
      if (foregroundPermissionApproved()) {
        foregroundOnlyLocationService?.subscribeToLocationUpdates()
          ?: Log.d(TAG, "Service Not Bound")
      } else {
        requestForegroundPermissions()
      }
    }
  }

  // Configure the session, using Lighting Estimation, and Depth mode.
  fun configureSession(session: Session) {
    session.configure(
      session.config.apply {
        // Enable Geospatial Mode.
        geospatialMode = Config.GeospatialMode.ENABLED

        lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR

        // Depth API is used if it is configured in Hello AR's settings.
        depthMode =
          if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
            Config.DepthMode.AUTOMATIC
          } else {
            Config.DepthMode.DISABLED
          }

        // Instant Placement is used if it is configured in Hello AR's settings.
        instantPlacementMode =
          if (instantPlacementSettings.isInstantPlacementEnabled) {
            InstantPlacementMode.LOCAL_Y_UP
          } else {
            InstantPlacementMode.DISABLED
          }
      }
    )

  }

  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<String>,
    results: IntArray
  ) {
    super.onRequestPermissionsResult(requestCode, permissions, results)
    if (!CameraPermissionHelper.hasCameraPermission(this)) {
      // Use toast instead of snackbar here since the activity will exit.
      Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG)
        .show()
      if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
        // Permission denied with checking "Do not ask again".
        CameraPermissionHelper.launchPermissionSettings(this)
      }
      finish()
    }

    if (!GeoPermissionsHelper.hasGeoPermissions(this)) {
      // Use toast instead of snackbar here since the activity will exit.
      Toast.makeText(this, "Camera and location permissions are needed to run this application", Toast.LENGTH_LONG)
        .show()
      if (!GeoPermissionsHelper.shouldShowRequestPermissionRationale(this)) {
        // Permission denied with checking "Do not ask again".
        GeoPermissionsHelper.launchPermissionSettings(this)
      }
      finish()
    }

    when (requestCode) {
      REQUEST_FOREGROUND_ONLY_PERMISSIONS_REQUEST_CODE -> when {
        results.isEmpty() ->
          // If user interaction was interrupted, the permission request
          // is cancelled and you receive empty arrays.
          Log.d(TAG, "User interaction was cancelled.")
        results[0] == PackageManager.PERMISSION_GRANTED ->
          // Permission was granted.
          foregroundOnlyLocationService?.subscribeToLocationUpdates()
        else -> {
          // Permission denied.
          Snackbar.make(
            findViewById(R.id.surfaceview),
            R.string.permission_denied_explanation,
            Snackbar.LENGTH_LONG
          )
            .setAction(R.string.settings) {
              // Build intent that displays the App settings screen.
              val intent = Intent()
              intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
              val uri = Uri.fromParts(
                "package",
                BuildConfig.APPLICATION_ID,
                null
              )
              intent.data = uri
              intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
              startActivity(intent)
            }
            .show()
        }
      }
    }
  }

  // TODO: Step 1.0, Review Permissions: Method checks if permissions approved.
  private fun foregroundPermissionApproved(): Boolean {
    return PackageManager.PERMISSION_GRANTED == ActivityCompat.checkSelfPermission(
      this,
      Manifest.permission.ACCESS_FINE_LOCATION
    )
  }

  // TODO: Step 1.0, Review Permissions: Method requests permissions.
  private fun requestForegroundPermissions() {
    val provideRationale = foregroundPermissionApproved()

    // If the user denied a previous request, but didn't check "Don't ask again", provide
    // additional rationale.
    if (provideRationale) {
      Snackbar.make(
        findViewById(R.id.surfaceview),
          R.string.permission_rationale,
        Snackbar.LENGTH_LONG
      )
        .setAction(R.string.ok) {
          // Request permission
          ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            REQUEST_FOREGROUND_ONLY_PERMISSIONS_REQUEST_CODE
          )
        }
        .show()
    } else {
      Log.d(TAG, "Request foreground only permission")
      ActivityCompat.requestPermissions(
        this,
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
        REQUEST_FOREGROUND_ONLY_PERMISSIONS_REQUEST_CODE
      )
    }
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus)
  }

  // Monitors connection to the while-in-use service.
  private val foregroundOnlyServiceConnection = object : ServiceConnection {

    override fun onServiceConnected(name: ComponentName, service: IBinder) {
      val binder = service as ForegroundOnlyLocationService.LocalBinder
      foregroundOnlyLocationService = binder.service
    }

    override fun onServiceDisconnected(name: ComponentName) {
      foregroundOnlyLocationService = null
    }
  }
}
