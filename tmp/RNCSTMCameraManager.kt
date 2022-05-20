package com.arsampleapp

import android.Manifest
import android.R
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.media.ImageReader.OnImageAvailableListener
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.view.TextureView.SurfaceTextureListener
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.common.MapBuilder
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.*

class RNCSTMCameraManager(var mCallerContext: ReactApplicationContext) : SimpleViewManager<TextureView?>() {
    val CAPTURE_COMMAND = 1111
    var textureView: TextureView
    var cameraId: String? = null
    var cameraDevice: CameraDevice? = null
    var cameraCaptureSession: CameraCaptureSession? = null
    var captureRequest: CaptureRequest? = null
    var captureRequestBuilder: CaptureRequest.Builder? = null
    private var imageDimensions: Size? = null
    private val imageReader: ImageReader? = null
    private var file: File? = null
    var mBackgroundHandler: Handler? = null
    var mBackgroundThread: HandlerThread? = null
    var textureListener: SurfaceTextureListener = object : SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            try {
                openCamera()
            } catch (e: CameraAccessException) {
                Log.d(ComponentTag, "Error in onSurfaceTextureAvailable:$e")
            }
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            return false
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
    }

    override fun getName(): String {
        return REACT_CLASS
    }

    /** Check if this device has a camera  */
    private fun checkCameraHardware(context: Context): Boolean {
        return if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            // this device has a camera
            true
        } else {
            // no camera on this device
            false
        }
    }

    private fun getCameraPermissions(context: Context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(mCallerContext.currentActivity!!, arrayOf(Manifest.permission.CAMERA), 100)
        }
    }

    private val stateCallback: CameraDevice.StateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            try {
                createCameraPreview()
            } catch (e: CameraAccessException) {
                Log.d(ComponentTag, "Error in onOpened:$e")
            }
        }

        override fun onDisconnected(camera: CameraDevice) {
            cameraDevice!!.close()
        }

        override fun onError(camera: CameraDevice, error: Int) {
            cameraDevice!!.close()
            cameraDevice = null
        }
    }

    @Throws(CameraAccessException::class)
    private fun createCameraPreview() {
        val surfaceTexture = textureView.surfaceTexture
        surfaceTexture!!.setDefaultBufferSize(imageDimensions!!.width, imageDimensions!!.height)
        val surface = Surface(surfaceTexture)
        captureRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        captureRequestBuilder!!.addTarget(surface)
        cameraDevice!!.createCaptureSession(Arrays.asList(surface), object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                if (cameraDevice == null) return
                cameraCaptureSession = session
                try {
                    updatePreview()
                } catch (e: CameraAccessException) {
                    e.printStackTrace()
                }
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                Log.d(ComponentTag, "onConfigure failed")
            }
        }, null)
    }

    @Throws(CameraAccessException::class)
    private fun updatePreview() {
        if (cameraDevice == null) return
        captureRequestBuilder!!.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
        cameraCaptureSession!!.setRepeatingRequest(captureRequestBuilder!!.build(), null, mBackgroundHandler)
    }

    @Throws(CameraAccessException::class)
    private fun openCamera() {
        Log.d(ComponentTag, "Open Camera was called from onSurface Available")
        val cameraManager = mCallerContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cameraId = cameraManager.cameraIdList[1]
        val cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId ?: return)
        val map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        imageDimensions = map!!.getOutputSizes(SurfaceTexture::class.java)[0]
        if (ActivityCompat.checkSelfPermission(mCallerContext, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(mCallerContext.currentActivity!!, arrayOf(Manifest.permission.CAMERA), 101)
        }
        if (ActivityCompat.checkSelfPermission(mCallerContext, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(mCallerContext.currentActivity!!, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 101)
        }
        cameraManager.openCamera(cameraId ?: return, stateCallback, null)
    }

    public override fun createViewInstance(context: ThemedReactContext): TextureView {
        textureView.surfaceTextureListener = textureListener
        return textureView
    }

    @Throws(CameraAccessException::class)
    private fun takePicture() {
        Log.d(ComponentTag, "takePicture is initiated!")
        if (cameraDevice == null) return
        val cameraManager = mCallerContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraDevice!!.id)
        var jpegSizes: Array<Size> = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!.getOutputSizes(ImageFormat.JPEG)
        var width = 1080
        var height = 1440
        if (jpegSizes.isNotEmpty()) {
            width = jpegSizes[0].width
            height = jpegSizes[0].height
        }
        val imageReader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1)
        val outputSurfaces: MutableList<Surface> = ArrayList<Surface>(2)
        outputSurfaces.add(imageReader.surface)
        outputSurfaces.add(Surface(textureView.surfaceTexture))
        val captureRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
        captureRequestBuilder.addTarget(imageReader.surface)
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
        val timeStampInLong = System.currentTimeMillis()
        val timeStamp = timeStampInLong.toString()
        file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString() + "/" + timeStamp + ".jpg")
        val readerListner = OnImageAvailableListener { reader ->
            var image: Image? = null
            image = reader.acquireLatestImage()
            val byteBuffer = image.planes[0].buffer
            val bytes = ByteArray(byteBuffer.capacity())
            byteBuffer[bytes]
            try {
                saveImageBytes(bytes)
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                image?.close()
            }
        }
        imageReader.setOnImageAvailableListener(readerListner, mBackgroundHandler)
        val captureCallback: CameraCaptureSession.CaptureCallback = object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                super.onCaptureCompleted(session, request, result)
                Log.d(ComponentTag, "Image is saved!")
                AlertDialog.Builder(mCallerContext.currentActivity!!)
                        .setTitle("Photo Saved!")
                        .setMessage("The photo has been successfully saved to your Photos Gallery.")
                        .setPositiveButton("👍", null)
                        .setIcon(R.drawable.ic_menu_gallery)
                        .show()
                try {
                    createCameraPreview()
                } catch (e: CameraAccessException) {
                    e.printStackTrace()
                }
            }
        }
        cameraDevice!!.createCaptureSession(outputSurfaces, object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                try {
                    session.capture(captureRequestBuilder.build(), captureCallback, mBackgroundHandler)
                } catch (e: CameraAccessException) {
                    e.printStackTrace()
                }
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {}
        }, mBackgroundHandler)
    }

    @Throws(IOException::class)
    private fun saveImageBytes(bytes: ByteArray) {
        var outputStream: OutputStream? = null
        outputStream = FileOutputStream(file)
        outputStream.write(bytes)
        outputStream.close()
    }

    /**
     * Map the "captureImage" command to an integer
     */
    override fun getCommandsMap(): Map<String, Int>? {
        return MapBuilder.of("captureImage", CAPTURE_COMMAND)
    }

    /**
     * Handle "captureImage" command called from JS
     */
    override fun receiveCommand(root: TextureView, commandId: String, args: ReadableArray?) {
        super.receiveCommand(root, commandId, args)
        val reactNativeViewId = args!!.getInt(0)
        val commandIdInt = commandId.toInt()
        when (commandIdInt) {
            CAPTURE_COMMAND -> try {
                takePicture()
            } catch (e: CameraAccessException) {
                e.printStackTrace()
            }
            else -> {
            }
        }
    }

    companion object {
        const val REACT_CLASS = "RNCSTMCamera"
        const val ComponentTag = "RNCSTMCamera"
    }

    init {
        textureView = TextureView(mCallerContext)
        if (checkCameraHardware(mCallerContext)) getCameraPermissions(mCallerContext)
    }
}