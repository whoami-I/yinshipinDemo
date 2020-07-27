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


/**
 * 截流法，根据现有文件的封装格式，读取视频文件的流，仅仅是读取流，而不做任何解码处理，
 * 选取我们需要的流数据加入到muxter中
 */
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
            val fileOutputStream = FileOutputStream(VIDEO_OUTPUT_PATH)
            val muxer =
                MediaMuxer(fileOutputStream.fd, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            for (i in 0..mediaExtractor.trackCount) {
                val trackFormat = mediaExtractor.getTrackFormat(i)
                val mimeType = trackFormat.getString(MediaFormat.KEY_MIME)
                //选择第一个遇到的videoTrack和第一个遇到的audioTrack
                if (mimeType.startsWith("video/")) {
                    if (videoTrackIndex == -1) videoTrackIndex = i
                    if (audioTrackIndex != -1) break
                } else if (mimeType.startsWith("audio/")) {
                    if (audioTrackIndex == -1) audioTrackIndex = i
                    if (videoTrackIndex != -1) break
                }
            }

            val startTime = 2000000L
            val endTime = 20000000L
            var startPresentationTime = 0L
            var startVideoPresentationTime = 0L
            val mediaMetadataRetriever = MediaMetadataRetriever()
            var bufferSize = -1
            var videoDstTrack = -1
            var audioDstTrack = -1
            if (videoTrackIndex != -1) {
                mediaExtractor.selectTrack(videoTrackIndex)
                mediaExtractor.seekTo(startTime, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                startVideoPresentationTime = mediaExtractor.sampleTime
                val trackFormat = mediaExtractor.getTrackFormat(videoTrackIndex)
//            fileOutputStream.close()
                videoDstTrack = muxer.addTrack(trackFormat)

                //获取视频的旋转角度
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

                if (trackFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    val newSize: Int = trackFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                    bufferSize = if (newSize > bufferSize) newSize else bufferSize
                }

            } else {
                Log.d("TAG", "no video in $VIDEO_PATH")
//                Toast.makeText(this, "no video in $VIDEO_PATH", Toast.LENGTH_LONG).show()
            }

            if (audioTrackIndex != -1) {
                mediaExtractor.selectTrack(audioTrackIndex)
                val trackFormat = mediaExtractor.getTrackFormat(audioTrackIndex)
                audioDstTrack = muxer.addTrack(trackFormat)
                if (trackFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    val newSize: Int = trackFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                    bufferSize = if (newSize > bufferSize) newSize else bufferSize
                }
            } else {
                Log.d("TAG", "no audio in $VIDEO_PATH")
            }
            //seek again when add audio and add video finish
            mediaExtractor.seekTo(startVideoPresentationTime, MediaExtractor.SEEK_TO_CLOSEST_SYNC)


            val bufferInfo = MediaCodec.BufferInfo()
            if (bufferSize < 0) {
                bufferSize = 1024 * 1024
            }
            val dstBuf = ByteBuffer.allocate(bufferSize)

            var startAudioPresentationTime = 0L
            var firstVideoPacket = false
            var firstAudioPacket = false
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
                    val isKeyFrame =
                        mediaExtractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0
                    val flags = if (isKeyFrame) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                    bufferInfo.flags = flags

                    val sampleTrackIndex = mediaExtractor.sampleTrackIndex
                    //视频和音频都需要知道第一个包的起始时间，后面的时间都需要减去这个起始时间
                    if (sampleTrackIndex == videoTrackIndex) {
                        if (!firstVideoPacket) {
                            firstVideoPacket = true
                            //startVideoPresentationTime = bufferInfo.presentationTimeUs
                        }
                        bufferInfo.presentationTimeUs =
                            bufferInfo.presentationTimeUs - startVideoPresentationTime
                        //写入视频数据
                        muxer.writeSampleData(videoDstTrack, dstBuf, bufferInfo)
                    } else if (sampleTrackIndex == audioTrackIndex) {
                        if (!firstAudioPacket) {
                            firstAudioPacket = true
                            startAudioPresentationTime = bufferInfo.presentationTimeUs
                        }
                        bufferInfo.presentationTimeUs =
                            bufferInfo.presentationTimeUs - startAudioPresentationTime
                        //写入音频数据
                        muxer.writeSampleData(audioDstTrack, dstBuf, bufferInfo)
                    }
                    mediaExtractor.advance()
                }
            } catch (e: Exception) {
                Log.d("TAG", "write video error:$e")
            } finally {
                mediaMetadataRetriever.release()
                mediaExtractor.release()
                muxer.stop()
            }
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        job?.cancel()
    }
}