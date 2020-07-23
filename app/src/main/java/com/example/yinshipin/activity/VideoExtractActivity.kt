package com.example.yinshipin.activity

import android.media.*
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.yinshipin.R
import kotlinx.android.synthetic.main.activity_video_extract.*
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

class VideoExtractActivity : AppCompatActivity() {
    val VIDEO_PATH = "/sdcard/test.mp4"
    val VIDEO_OUTPUT_PATH = "/sdcard/output.mp4"
    lateinit var job: Job
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_extract)
        if (!File(VIDEO_PATH).exists()) {
            Toast.makeText(this, VIDEO_PATH, Toast.LENGTH_LONG).show()
            finish()
        }
        job = GlobalScope.launch {
            extractVideoinfo()
            withContext(Dispatchers.Main) {
                progressBar.visibility = View.INVISIBLE
                Toast.makeText(
                    this@VideoExtractActivity,
                    "extract $VIDEO_OUTPUT_PATH success",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    suspend fun extractVideoinfo() {
        withContext(Dispatchers.IO) {
            val mediaExtractor = MediaExtractor()
            var videoTrackIndex: Int = -1
            var audioTrackIndex = -1
            mediaExtractor.setDataSource(VIDEO_PATH)
            for (i in 0..mediaExtractor.trackCount) {
                val trackFormat = mediaExtractor.getTrackFormat(i)
                val mimeType = trackFormat.getString(MediaFormat.KEY_MIME)
                if (mimeType.startsWith("video/")) {
                    videoTrackIndex = i
                    if (audioTrackIndex != -1) break
                } else if (mimeType.startsWith("audio/")) {
                    audioTrackIndex = i
                    if (videoTrackIndex != -1) break
                }
            }

            val startTime = 2000000L
            val endTime = 20000000L
            var startPresentationTime = 0L
            var startVideoPresentationTime = 0L
            if (videoTrackIndex != -1) {
                mediaExtractor.selectTrack(videoTrackIndex)
                mediaExtractor.seekTo(startTime, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                startPresentationTime = mediaExtractor.sampleTime
                val trackFormat = mediaExtractor.getTrackFormat(videoTrackIndex)
                val fileOutputStream = FileOutputStream(VIDEO_OUTPUT_PATH)
                val muxer =
                    MediaMuxer(fileOutputStream.fd, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
//            fileOutputStream.close()
                muxer.addTrack(trackFormat)
                val mediaMetadataRetriever = MediaMetadataRetriever()
                mediaMetadataRetriever.setDataSource(VIDEO_PATH)
                val degreesString: String = mediaMetadataRetriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
                )
                if (degreesString != null) {
                    val degrees = degreesString.toInt()
                    if (degrees >= 0) {
                        muxer.setOrientationHint(degrees)
                    }
                }
                var bufferSize = -1
                if (trackFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    val newSize: Int = trackFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                    bufferSize = if (newSize > bufferSize) newSize else bufferSize
                }
                if (bufferSize < 0) {
                    bufferSize = 1024 * 1024
                }
                val dstBuf = ByteBuffer.allocate(bufferSize)
                val bufferInfo = MediaCodec.BufferInfo()

                try {
                    muxer.start()
                    while (isActive) {
                        bufferInfo.size = mediaExtractor.readSampleData(dstBuf, 0)
                        if (bufferInfo.size < 0) {
                            Log.d("TAG", "size < 0")
                            break
                        }
                        bufferInfo.offset = 0
                        bufferInfo.presentationTimeUs = mediaExtractor.sampleTime
                        if (bufferInfo.presentationTimeUs > endTime) {
                            Log.d(
                                "TAG",
                                "end presentationTimeUs ->${bufferInfo.presentationTimeUs}"
                            )
                            break
                        }
                        bufferInfo.presentationTimeUs =
                            bufferInfo.presentationTimeUs - startPresentationTime
                        val isKeyFrame =
                            mediaExtractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0
                        val flags = if (isKeyFrame) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                        bufferInfo.flags = flags
                        muxer.writeSampleData(videoTrackIndex, dstBuf, bufferInfo)
                        mediaExtractor.advance()
                    }
                } catch (e: Exception) {
                    Log.d("TAG", "write video error:$e")
                } finally {
                    mediaMetadataRetriever.release()
                    mediaExtractor.release()
                    muxer.stop()
                }
            } else {
                Log.d("TAG", "no video in $VIDEO_PATH")
//                Toast.makeText(this, "no video in $VIDEO_PATH", Toast.LENGTH_LONG).show()
            }
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        job?.cancel()
    }
}