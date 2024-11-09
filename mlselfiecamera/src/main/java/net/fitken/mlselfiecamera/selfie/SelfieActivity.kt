package net.fitken.mlselfiecamera.selfie

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.ProgressDialog
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import net.fitken.mlselfiecamera.databinding.ActivitySelfieBinding
import net.fitken.mlselfiecamera.camera.CameraSource
import net.fitken.mlselfiecamera.facedetection.FaceContourDetectorProcessor
import net.fitken.mlselfiecamera.util.PermissionUtil
import net.fitken.rose.Rose
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class SelfieActivity : AppCompatActivity(),
    FaceContourDetectorProcessor.FaceContourDetectorListener {

    companion object {
        const val KEY_IMAGE_PATH = "image_path"
        private const val KEY_TEXT_BACK = "text_back"
        private const val KEY_TEXT_DESCRIPTION = "text_description"
        private const val PERMISSION_CAMERA_REQUEST_CODE = 2

        fun createBundle(textBack: String, textDescription: String): Bundle {
            val bundle = Bundle()
            bundle.putString(KEY_TEXT_BACK, textBack)
            bundle.putString(KEY_TEXT_DESCRIPTION, textDescription)
            return bundle
        }
    }

    private lateinit var binding: ActivitySelfieBinding
    private var mCameraSource: CameraSource? = null
    private var mCapturedBitmap: Bitmap? = null
    private lateinit var mFaceContourDetectorProcessor: FaceContourDetectorProcessor
    private lateinit var progressDialog: ProgressDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySelfieBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val textBack = intent?.extras?.getString(KEY_TEXT_BACK)
        val textDescription = intent?.extras?.getString(KEY_TEXT_DESCRIPTION)

        textBack?.let {
            binding.tvBack.text = it
        }

        textDescription?.let {
            binding.tvDescription.text = it
        }

        if (PermissionUtil.isHavePermission(this, PERMISSION_CAMERA_REQUEST_CODE, Manifest.permission.CAMERA)) {
            createCameraSource()
        }

        startCameraSource()

        binding.tvBack.setOnClickListener {
            onBackPressed()
        }

        binding.ivCapture.setOnClickListener {
            showProgressDialog()
            createSelfiePictureAndReturn()
        }

        binding.btnCloseActivity.setOnClickListener {
            val dialog = ProgressDialog(this)
            dialog.setMessage("Exiting...")
            dialog.setCancelable(false)
            dialog.show()

            binding.root.postDelayed({
                dialog.dismiss()  // 다이얼로그 닫고 종료
                finish()
            }, 500)  // 500ms 후 종료
        }

    }

    private fun showProgressDialog() {
        progressDialog = ProgressDialog(this)
        progressDialog.setMessage("Saving your selfie...")
        progressDialog.setCancelable(false)
        progressDialog.show()
    }

    private fun dismissProgressDialog() {
        if (::progressDialog.isInitialized && progressDialog.isShowing) {
            progressDialog.dismiss()
        }
    }

    private fun restartSelfieCaptureScreen() {
        binding.ivCapture.alpha = 1F
        binding.ivCapture.isEnabled = true
    }

    private fun createSelfiePictureAndReturn() {
        Thread {
            val file = File(cacheDir, "selfie.jpg")
            file.createNewFile()

            try {
                val bos = ByteArrayOutputStream()
                mCapturedBitmap?.compress(Bitmap.CompressFormat.PNG, 100, bos)
                val bitmapData = bos.toByteArray()

                val fos = FileOutputStream(file)
                fos.write(bitmapData)
                fos.flush()
                fos.close()

                val intent = Intent().apply {
                    putExtra(KEY_IMAGE_PATH, file.absolutePath)
                }
                setResult(Activity.RESULT_OK, intent)

            } catch (e: IOException) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, "Error saving the selfie", Toast.LENGTH_SHORT).show()
                }
            } finally {
                Thread.sleep(500)

                runOnUiThread {
                    dismissProgressDialog()
                    restartSelfieCaptureScreen()
                }
            }
        }.start()
    }

    private fun createCameraSource() {
        // If there's no existing cameraSource, create one.
        if (mCameraSource == null) {
            mCameraSource = CameraSource(this, binding.faceOverlay)
        }

        Rose.error("Using Face Contour Detector Processor")
        mFaceContourDetectorProcessor = FaceContourDetectorProcessor(this, true, mCameraSource!!)
        mCameraSource?.setMachineLearningFrameProcessor(mFaceContourDetectorProcessor)
    }

    /**
     * Starts or restarts the camera source, if it exists. If the camera source doesn't exist yet
     * (e.g., because onResume was called before the camera source was created), this will be called
     * again when the camera source is created.
     */
    private fun startCameraSource() {
        mCameraSource?.let {
            try {
                binding.cameraPreview.start(mCameraSource, binding.faceOverlay)
            } catch (e: IOException) {
                Rose.error("Unable to start camera source.  $e")
                mCameraSource?.release()
                mCameraSource = null
            }
        }
    }

    public override fun onResume() {
        super.onResume()
        startCameraSource()
    }

    /** Stops the camera.  */
    override fun onPause() {
        super.onPause()
        binding.cameraPreview.stop()
    }

    public override fun onDestroy() {
        super.onDestroy()
        mCameraSource?.release()
    }

    @SuppressLint("MissingPermission")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSION_CAMERA_REQUEST_CODE -> {
                if (PermissionUtil.isPermissionGranted(requestCode, PERMISSION_CAMERA_REQUEST_CODE, grantResults)) {
                    createCameraSource()
                } else {
                    onBackPressed()
                }
            }
        }
    }

    override fun onCapturedFace(originalCameraImage: Bitmap) {
        mCapturedBitmap = originalCameraImage
        binding.ivCapture.alpha = 1F
        binding.ivCapture.isEnabled = true
    }

    override fun onNoFaceDetected() {
        mCapturedBitmap = null
        binding.ivCapture.alpha = 0.3F
        binding.ivCapture.isEnabled = false
    }

    override fun onFaceDirectionDetected(isLookingRight: Boolean, isLookingLeft: Boolean) {
        when {
            isLookingRight && !isLookingLeft -> binding.faceDirection.text = "➡️ Looking to the right"
            !isLookingRight && isLookingLeft -> binding.faceDirection.text = "⬅️ Looking to the left"
            else -> binding.faceDirection.text = "↔️ Looking straight ahead"
        }
    }

    override fun onShowFaceDistance(string: String) {
        binding.faceDistance.text = string
    }
}