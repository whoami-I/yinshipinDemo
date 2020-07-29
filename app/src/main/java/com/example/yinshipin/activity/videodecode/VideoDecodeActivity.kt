package com.example.yinshipin.activity.videodecode

import android.media.*
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.yinshipin.R
import kotlinx.android.synthetic.main.activity_video_extract.*
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

/**
 */
class VideoDecodeActivity : AppCompatActivity() {
    var TAG = "VideoDecodeActivity"
    val VIDEO_PATH = "/sdcard/test.mp4"
    val VIDEO_OUTPUT_PATH = "/sdcard/output.mp4"
    lateinit var job: Job
    val eglThread = HandlerThread("GL thread")
    lateinit var eglHandler: Handler
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_extract)
        if (!File(VIDEO_PATH).exists()) {
            Toast.makeText(this, VIDEO_PATH, Toast.LENGTH_LONG).show()
            finish()
        }
        eglThread.start()
        job = GlobalScope.launch {
            extractVideoinfo()
            withContext(Dispatchers.Main) {
                progressBar.visibility = View.INVISIBLE
                Toast.makeText(
                    this@VideoDecodeActivity,
                    "extract $VIDEO_OUTPUT_PATH success",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    var startTime = 2000000L
    val endTime = 20000000L
    var mEncodePresentTimeUs: Long = 0
    var mPresentationTimeUs1: Long = 0
    var videoOutputIndex = -1
    suspend fun extractVideoinfo() {
        withContext(Dispatchers.IO) {
            //start and end time of the clip video
            eglHandler = Handler(eglThread.looper)

            // type the output video
            val MIME_VIDEO_TYPE = "video/avc"
            val videoExtractor = MediaExtractor()
            var videoTrackIndex: Int = -1
            var audioTrackIndex = -1
            var cropWidth = 0
            var cropHeight = 0
            val fileOutputStream = FileOutputStream(VIDEO_OUTPUT_PATH)
            videoExtractor.setDataSource(VIDEO_PATH)
            val muxer =
                MediaMuxer(fileOutputStream.fd, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var rotation = 0

            val metadataRetriever = MediaMetadataRetriever()
            metadataRetriever.setDataSource(VIDEO_PATH)
            if (null != metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)) {
                rotation = Integer.valueOf(
                    metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                )
            }
            if (null != metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)) {
                cropWidth =
                    metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                        .toInt()
            }
            if (null != metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)) {
                cropHeight =
                    metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                        .toInt()
            }

            if (90 == rotation || 270 == rotation) {
                //交换宽高
                var tmp = 0;
                tmp = cropWidth
                cropWidth = cropHeight
                cropHeight = tmp
            }
            if (cropHeight <= 0 || cropWidth <= 0) {
                Log.d(TAG, "cropHeight:$cropHeight  cropWidth:$cropWidth can not < 0")
                return@withContext
            }
            //
            var videoEncodeCodecInfo: MediaCodecInfo? = getSupportCodecInfo(MIME_VIDEO_TYPE)
            if (videoEncodeCodecInfo == null) {
                Log.d(TAG, "device do not support video/avc type")
                return@withContext
            }
            //width and height range the device support
            val videoCapabilities =
                videoEncodeCodecInfo.getCapabilitiesForType(MIME_VIDEO_TYPE).videoCapabilities
            val supportedWidths = videoCapabilities.supportedWidths
            val supportedHeights = videoCapabilities.supportedHeights
            if (cropHeight <= supportedHeights.lower || cropHeight >= supportedHeights.upper || cropWidth <= supportedWidths.lower || cropWidth >= supportedWidths.upper) {
                Log.e(
                    TAG,
                    "video width height not support, width: " + cropWidth + "cropHeight: " + cropHeight
                )
                return@withContext
            }

            var mime: String? = ""
            for (i in 0..videoExtractor.trackCount) {
                val trackFormat = videoExtractor.getTrackFormat(i)
                var mimeType: String? = trackFormat.getString(MediaFormat.KEY_MIME)
                //选择第一个遇到的videoTrack和第一个遇到的audioTrack
                if (mimeType!!.startsWith("video/")) {
                    if (videoTrackIndex == -1 && getSupportCodecInfo(mimeType) != null) {
                        mime = mimeType
                        videoTrackIndex = i
//                    if (audioTrackIndex != -1)
                        break
                    }
                }
//                else if (mimeType.startsWith("audio/")) {
//                    if (audioTrackIndex == -1) audioTrackIndex = i
//                    if (videoTrackIndex != -1) break
//                }
            }
            if (videoTrackIndex == -1) {
                Log.d(TAG, "can not find support video in file:${VIDEO_PATH}")
                return@withContext
            }
            videoExtractor.selectTrack(videoTrackIndex)
            videoExtractor.seekTo(startTime, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val startIFrameTime = videoExtractor.sampleTime
            if (startIFrameTime > startTime) {
                startTime = startIFrameTime
                Log.d(TAG, "startIFrameTime :$startIFrameTime larger than startTime: $startTime")
            }
            val BIT_RATE = cropWidth * cropHeight * 3 * 8
            /* encode */
            val frameRate: Int
            var format = videoExtractor.getTrackFormat(videoTrackIndex)
            frameRate = try {
                format.getInteger(MediaFormat.KEY_FRAME_RATE)
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "can not get frame rate"
                )
                25
            }

            val mediaFormat =
                MediaFormat.createVideoFormat(MIME_VIDEO_TYPE, cropWidth, cropHeight)
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            // set encode data source is surface
            mediaFormat.setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            )
            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            var videoEncoder: MediaCodec = MediaCodec.createEncoderByType(MIME_VIDEO_TYPE)
            videoEncoder.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            var videoDecoder = MediaCodec.createDecoderByType(mime as String)
            eglHandler.post {
                val encoderSurface = videoEncoder.createInputSurface()
                videoEncoder.start()
                var glHelper: GLHelper? = null
                var eglHelper: EglHelper? = null
                try {
                    eglHelper = EglHelper(encoderSurface)
                    eglHelper.initEnv()
                    eglHelper.createSurface()
                    glHelper = GLHelper()
                    glHelper.initFramebuffer(cropWidth, cropHeight)
                } catch (e: java.lang.Exception) {
                    encoderSurface.release()
                    Log.e(TAG, e.toString())
                    return@post
                }
                val surfaceTexture = glHelper.getSurfaceTexture(cropWidth, cropHeight)
                surfaceTexture.setDefaultBufferSize(cropWidth, cropHeight)
                surfaceTexture.setOnFrameAvailableListener {
                    eglHelper.setPresentationTimeNs(mEncodePresentTimeUs)
                    //when new data, the gl will be draw and then put into the video encode queue buffer
                    Log.i(
                        TAG,
                        "mPresentationTimeUs: " + mPresentationTimeUs1 + " mStarttime " + startTime + " mEndTime" + endTime
                    );
                    if (mPresentationTimeUs1 in startTime..endTime) {
                        glHelper.drawFrameBuffer()
                        Log.d(TAG, "mFramebuffer.drawFrameBuffer");
                        eglHelper.swap()
                    } else {
                        glHelper.updateTexImage()
                    }
//                    synchronized(mVideoDecoderObject) {
//                        isDraw = true
//                        mVideoDecoderObject.notifyAll()
//                    }
                }
                videoDecoder.configure(
                    format,
                    Surface(surfaceTexture),
                    null,
                    0 /* Decoder */
                )
                videoDecoder.start()
            }

            //start decode
            //status > 0:normal
            //status < 0 :error
            //status =0:decode finish
            var status = 1
            var result = 1
            loop@ while (true) {
                val bufferInfo = MediaCodec.BufferInfo()
                status = decodeVideo(videoDecoder, videoExtractor, videoEncoder)
                if (status > 0) {
                    //decode normal
                    val dequeueOutputBufferIndex =
                        videoDecoder.dequeueOutputBuffer(bufferInfo, 1000000)
                    when (dequeueOutputBufferIndex) {
                        MediaCodec.INFO_TRY_AGAIN_LATER -> continue@loop
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> continue@loop
                        MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> continue@loop
                    }
                    mPresentationTimeUs1 = bufferInfo.presentationTimeUs
                    if (dequeueOutputBufferIndex >= 0) {
//start encode
                        mEncodePresentTimeUs = mPresentationTimeUs1 - startTime
                        videoDecoder.releaseOutputBuffer(dequeueOutputBufferIndex, true)
                        delay(50)
                        val bufferInfoEncode = MediaCodec.BufferInfo()
                        val outputBufferIndex =
                            videoEncoder.dequeueOutputBuffer(bufferInfoEncode, 1000000)
                        if (outputBufferIndex >= 0) {
                            val outputBuffer = videoEncoder.getOutputBuffer(outputBufferIndex)
                            if (bufferInfoEncode.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                                bufferInfoEncode.size = 0
                            }
                            if (bufferInfoEncode.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                Log.d(
                                    TAG,
                                    "drainEncoder: reach the end@!"
                                )
                                bufferInfoEncode.set(0, 0, 0, bufferInfoEncode.flags)
                                status = 0
                            }
                            if (bufferInfoEncode.size > 0) {
                                outputBuffer?.position(bufferInfoEncode.offset)
                                outputBuffer?.limit(bufferInfoEncode.offset + bufferInfoEncode.size)
                                bufferInfoEncode.presentationTimeUs =
                                    mPresentationTimeUs1 - startTime
                                if (videoOutputIndex < 0) {
                                    val outputFormat = videoEncoder.outputFormat
                                    videoOutputIndex = muxer.addTrack(outputFormat)
                                    muxer.start()
                                }
                                muxer.writeSampleData(
                                    videoTrackIndex,
                                    outputBuffer as ByteBuffer,
                                    bufferInfoEncode
                                )
                                videoEncoder.releaseOutputBuffer(outputBufferIndex, false)
                                status = 1
                            }
                        } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            continue@loop
                        } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                            continue@loop
                        } else if (outputBufferIndex == -1) {
                            continue@loop
                        } else {
                            status = -1
                        }

                        if (status <= 0) {
                            break
                        }

                    }
                } else if (status == 0) {
                    break
                } else {
                    break
                }
            }//out while

//                eglHandler.post {
            //eglHelper.release()
//                }
//                try {
//                    videoDecoder.signalEndOfInputStream()
            videoEncoder.signalEndOfInputStream()
            videoDecoder.release()
            videoEncoder.release()
            videoExtractor.release()
            muxer.stop()
//                }catch (e:java.lang.Exception){
//                    Log.d(TAG,"release error $e")
//                }

        }
    }

//    private fun encodeVideo(videoEncoder: MediaCodec): Int {
//        val bufferInfo = MediaCodec.BufferInfo()
//        val outputBufferIndex = videoEncoder.dequeueOutputBuffer(bufferInfo, 1000000)
//        if (outputBufferIndex > 0) {
//            val outputBuffer = videoEncoder.getOutputBuffer(outputBufferIndex)
//
//        } else if () {
//            return -1
//        }
//    }

    fun decodeVideo(
        videoDecoder: MediaCodec, videoExtractor: MediaExtractor,
        videoEncoder: MediaCodec
    ): Int {
        val dequeueInputBufferIndex = videoDecoder.dequeueInputBuffer(1000000)
        if (dequeueInputBufferIndex >= 0) {
            val inputBuffer = videoDecoder.getInputBuffer(dequeueInputBufferIndex)
            mPresentationTimeUs1 = videoExtractor.sampleTime
            if (mPresentationTimeUs1 > endTime) {
                //over
                videoEncoder.queueInputBuffer(
                    dequeueInputBufferIndex,
                    0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                )
                return 0
            }
            val size = videoExtractor.readSampleData(inputBuffer as ByteBuffer, 0)
            return if (size > 0) {
                videoDecoder.queueInputBuffer(
                    dequeueInputBufferIndex,
                    0, size, mPresentationTimeUs1, videoExtractor.sampleFlags
                )
                videoExtractor.advance()
                1
            } else {
                //over
                videoDecoder.queueInputBuffer(
                    dequeueInputBufferIndex,
                    0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                )
                0
            }
        } else if (dequeueInputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
            return 1
        } else {
            //may no avaible input buffer,so check for more times
            //error
            return -1
        }
    }

    fun getSupportCodecInfo(mimeType: String): MediaCodecInfo? {
        val codecInfos = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
        for (codecInfo in codecInfos) {
            for (supportType in codecInfo.supportedTypes) {
                if (mimeType.equals(supportType, true)) {
                    return codecInfo

                }
            }
        }
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        job?.cancel()
    }
}