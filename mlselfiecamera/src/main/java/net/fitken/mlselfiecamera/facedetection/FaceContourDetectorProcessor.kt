package net.fitken.mlselfiecamera.facedetection

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Camera
import android.os.SystemClock
import android.util.Log
import android.view.SurfaceView
import com.google.android.gms.tasks.Task
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.common.FirebaseVisionPoint
import com.google.firebase.ml.vision.face.FirebaseVisionFace
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetector
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions
import com.google.firebase.ml.vision.face.FirebaseVisionFaceLandmark
//import kotlinx.android.synthetic.main.activity_selfie.face_direction
import net.fitken.mlselfiecamera.camera.CameraImageGraphic
import net.fitken.mlselfiecamera.camera.CameraSource
import net.fitken.mlselfiecamera.camera.FrameMetadata
import net.fitken.mlselfiecamera.camera.GraphicOverlay
import net.fitken.rose.Rose
import java.io.IOException
import java.lang.Math.abs
import java.util.Objects


/**
 * Face Contour Demo.
 */
class FaceContourDetectorProcessor(
//    private var mType: FaceDetectionType = FaceDetectionType.IDENTITY,
    faceContourDetectorListener: FaceContourDetectorListener? = null,
    isShowDot: Boolean = true,
    cameraSource: CameraSource
) :
    VisionProcessorBase<List<FirebaseVisionFace>>() {


    private val detector: FirebaseVisionFaceDetector
    private var mFaceContourDetectorListener: FaceContourDetectorListener? = null
    private var mIsAllowDetect = true

    private var mStartTime = SystemClock.uptimeMillis()

    private var rectWidth: Float = 0.0f

    private val IMAGE_WIDTH = 1024
    private val IMAGE_HEIGHT = 1024
    private val AVERAGE_EYE_DISTANCE = 63 // in mm

    private var F = cameraSource.F
    private var sensorX: Float = cameraSource.sensorX
    private var sensorY: Float = cameraSource.sensorY //camera sensor dimensions

    init {
        val options = FirebaseVisionFaceDetectorOptions.Builder()
            .setPerformanceMode(FirebaseVisionFaceDetectorOptions.FAST)
            .setContourMode(if (isShowDot) FirebaseVisionFaceDetectorOptions.ALL_CONTOURS else FirebaseVisionFaceDetectorOptions.NO_CONTOURS)
            .setLandmarkMode(FirebaseVisionFaceDetectorOptions.ALL_LANDMARKS)
//            .setClassificationMode(FirebaseVisionFaceDetectorOptions.ALL_CLASSIFICATIONS)
            .build()

        detector = FirebaseVision.getInstance().getVisionFaceDetector(options)
        mFaceContourDetectorListener = faceContourDetectorListener
    }

    override fun stop() {
        try {
            detector.close()
        } catch (e: IOException) {
            Log.e(TAG, "Exception thrown while trying to close Face Contour Detector: $e")
        }
    }

    fun stopDetect() {
        mIsAllowDetect = false
    }

    fun restart() {
        mIsAllowDetect = true
    }

    override fun detectInImage(image: FirebaseVisionImage): Task<List<FirebaseVisionFace>> {
        val faces = detector.detectInImage(image)
        Rose.error("temp")
        return faces
    }

    override fun onSuccess(
        originalCameraImage: Bitmap?,
        results: List<FirebaseVisionFace>,
        frameMetadata: FrameMetadata,
        graphicOverlay: GraphicOverlay
    ) {
        graphicOverlay.clear()

        originalCameraImage?.let {
            val imageGraphic = CameraImageGraphic(graphicOverlay, it)
            graphicOverlay.add(imageGraphic)
        }

        results.forEach { face ->
            rectWidth = face.boundingBox.width().toFloat()
            val faceGraphic = FaceContourGraphic(graphicOverlay, face)
            val isLookingRight = isLookingRightSide(face)
            val isLookingLeft = isLookingLeftSide(face)
//            val isLookingUp = isLookingUpSide(face)
//            val isLookingDown = isLookingDownSide(face)
            mFaceContourDetectorListener?.onFaceDirectionDetected(isLookingRight, isLookingLeft)
            val distance = calculateDistance(face, isLookingRight, isLookingLeft)
            mFaceContourDetectorListener?.onShowFaceDistance(distance)
            graphicOverlay.add(faceGraphic)
        }

        if (results.isEmpty()) {
            mFaceContourDetectorListener?.onNoFaceDetected()
        } else {
            originalCameraImage?.let { mFaceContourDetectorListener?.onCapturedFace(it) }
        }

        graphicOverlay.postInvalidate()
    }

    override fun onFailure(e: Exception) {
        Log.e(TAG, "Face detection failed $e")
    }

    companion object {
        private const val TAG = "FaceContourDetectorProc"
        private const val BUFFER_SIZE_FACE = 20
    }

    interface FaceContourDetectorListener {
        fun onCapturedFace(originalCameraImage: Bitmap)
        fun onNoFaceDetected()
        fun onFaceDirectionDetected(isLookingRight: Boolean, isLookingLeft: Boolean)
        fun onShowFaceDistance(string: String)
    }

    fun isNearby(point1: FirebaseVisionPoint?, point2: FirebaseVisionPoint?, errorTolerance: Double): Boolean {
        val x = Math.pow((point1!!.x - point2!!.x).toDouble(), 2.0)
        val y = Math.pow((point1!!.y - point2!!.y).toDouble(), 2.0)
        val result = Math.sqrt(x+y)

        return (result < ((errorTolerance) * (rectWidth*1.5)))
    }

    fun isHigher(point1: FirebaseVisionPoint?, point2: FirebaseVisionPoint?): Boolean {
        return point1!!.y < point2!!.y
    }

    fun isLookingUpSide(face: FirebaseVisionFace): Boolean {
        val leftCheekPosition = Objects.requireNonNull(face.getLandmark(FirebaseVisionFaceLandmark.LEFT_CHEEK))?.position
        val rightCheekPosition = Objects.requireNonNull(face.getLandmark(FirebaseVisionFaceLandmark.RIGHT_CHEEK))?.position
        val leftEarPosition = Objects.requireNonNull(face.getLandmark(FirebaseVisionFaceLandmark.LEFT_EAR))?.position
        val rightEarposition = Objects.requireNonNull(face.getLandmark(FirebaseVisionFaceLandmark.RIGHT_EAR))?.position

        val rightCheekIsHigherThanRightEar = isHigher(rightCheekPosition, rightEarposition)
        val leftCheekIsHigherThanLeftEar = isHigher(leftCheekPosition, leftEarPosition)

        return rightCheekIsHigherThanRightEar && leftCheekIsHigherThanLeftEar
    }

    fun isLookingDownSide(face: FirebaseVisionFace): Boolean {
        val nosePosition = Objects.requireNonNull(face.getLandmark(FirebaseVisionFaceLandmark.NOSE_BASE))?.position
        val mouthBottomPosition = Objects.requireNonNull(face.getLandmark(FirebaseVisionFaceLandmark.MOUTH_BOTTOM))?.position
        val mouthBottomAndNoseIsNearby = isNearby(mouthBottomPosition, nosePosition, 0.18)

        return mouthBottomAndNoseIsNearby
    }

    fun isLookingRightSide(face: FirebaseVisionFace): Boolean {
        val leftCheekPosition = Objects.requireNonNull(face.getLandmark(FirebaseVisionFaceLandmark.LEFT_CHEEK))?.position
        val nosePosition = Objects.requireNonNull(face.getLandmark(FirebaseVisionFaceLandmark.NOSE_BASE))?.position
        val mouthLeftPosition = Objects.requireNonNull(face.getLandmark(FirebaseVisionFaceLandmark.MOUTH_LEFT))?.position
        val mouthBottomPosition = Objects.requireNonNull(face.getLandmark(FirebaseVisionFaceLandmark.MOUTH_BOTTOM))?.position

        val leftCheekAndNoseIsNearby = isNearby(leftCheekPosition, nosePosition, 0.15)
        val mouthLeftAndMouthBottomIsNearby = isNearby(mouthLeftPosition, mouthBottomPosition, 0.1)

        return leftCheekAndNoseIsNearby && mouthLeftAndMouthBottomIsNearby
    }

    fun isLookingLeftSide(face: FirebaseVisionFace): Boolean {
        val rightCheekPosition = Objects.requireNonNull(face.getLandmark(FirebaseVisionFaceLandmark.RIGHT_CHEEK))?.position
        val nosePosition = Objects.requireNonNull(face.getLandmark(FirebaseVisionFaceLandmark.NOSE_BASE))?.position
        val mouthRightPosition = Objects.requireNonNull(face.getLandmark(FirebaseVisionFaceLandmark.MOUTH_RIGHT))?.position
        val mouthBottomPosition = Objects.requireNonNull(face.getLandmark(FirebaseVisionFaceLandmark.MOUTH_BOTTOM))?.position

        val rightCheekAndNoseIsNearby = isNearby(rightCheekPosition, nosePosition, 0.15)
        val mouthRightAndMouthBottomIsNearby = isNearby(mouthRightPosition, mouthBottomPosition, 0.1)
        return rightCheekAndNoseIsNearby && mouthRightAndMouthBottomIsNearby
    }

    fun calculateDistance(face: FirebaseVisionFace, isLookingRight: Boolean, isLookingLeft: Boolean): String {
        val leftEyePos = face.getLandmark(FirebaseVisionFaceLandmark.LEFT_EYE)?.position
        val rightEyePos = face.getLandmark(FirebaseVisionFaceLandmark.RIGHT_EYE)?.position
        val deltaX = abs(leftEyePos!!.x - rightEyePos!!.x)
        val deltaY = abs(leftEyePos!!.y - rightEyePos!!.y)

        val distance: Float = if (deltaX >= deltaY) {
            F * (AVERAGE_EYE_DISTANCE / sensorX) * (IMAGE_WIDTH / deltaX)
        } else {
            F * (AVERAGE_EYE_DISTANCE / sensorY) * (IMAGE_HEIGHT / deltaY)
        }

        if (isLookingRight && !isLookingLeft && distance.toInt() <= 450) {
            val smileUnicode = 0x1F604
            return "${String(Character.toChars(smileUnicode))} very good!"
        }
        else if (!isLookingRight && isLookingLeft && distance.toInt() <= 450) {
            val smileUnicode = 0x1F604
            return "${String(Character.toChars(smileUnicode))} very good!"
        }
        else if (distance.toInt() <= 350) {
            val smileUnicode = 0x1F604
            return "${String(Character.toChars(smileUnicode))} very good!"
        }else {
            val confoundedUnicode = 0x1F616
            return "${String(Character.toChars(confoundedUnicode))} Come a bit closer!"
        }

//        if (distance.toInt() <= 350) {
//            val smileUnicode = 0x1F604
//            return "${String(Character.toChars(smileUnicode))} very good!"
//        } else {
//            val confoundedUnicode = 0x1F616
//            return "${String(Character.toChars(confoundedUnicode))} Come a bit closer!"
//        }
    }
}