package com.example.yinshipin.activity

import android.annotation.SuppressLint
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraDevice.StateCallback
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Bundle
import android.view.Surface
import android.view.SurfaceHolder
import androidx.appcompat.app.AppCompatActivity
import com.example.yinshipin.R
import kotlinx.android.synthetic.main.activity_camera_preview.*

/**
 * 使用camera进行预览,使用了camera2 api，camera2的使用流程是这样的;
 * 1.通过系统服务获取CameraManager
 * 2.拿到CameraManager 打开camera，这里需要实现打开失败，成功的接口 ，这里会有CameraDevice实例传入
 * 3.通过CameraDevice实例创建capture session，添加session创建情况的回调
 * 4.在session创建好了之后，通过CameraDevice创建createCaptureRequest
 */
class CameraPreviewActivity : AppCompatActivity() {
    lateinit var mCameraDevice: CameraDevice
    lateinit var mPreviewSurface: Surface
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_preview)
        surfaceview.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceChanged(
                holder: SurfaceHolder?,
                format: Int,
                width: Int,
                height: Int
            ) {

            }

            override fun surfaceDestroyed(holder: SurfaceHolder?) {
            }

            @SuppressLint("MissingPermission")
            override fun surfaceCreated(holder: SurfaceHolder?) {
                mPreviewSurface = holder!!.surface
                val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
                //打开摄像头的ID，0是后摄，1是前摄
                cameraManager.openCamera("0", object : StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        mCameraDevice = camera
                        camera.createCaptureSession(
                            mutableListOf(holder!!.surface),
                            object : CameraCaptureSession.StateCallback() {
                                override fun onConfigureFailed(session: CameraCaptureSession) {
                                    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
                                }

                                override fun onConfigured(session: CameraCaptureSession) {
                                    val builder: CaptureRequest.Builder
                                    builder =
                                        mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                                    builder.addTarget(mPreviewSurface)
                                    session.setRepeatingRequest(
                                        builder.build(),
                                        null,
                                        null
                                    )
                                }

                            }, null
                        )
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                    }

                    override fun onClosed(camera: CameraDevice) {
                        super.onClosed(camera)
                    }
                }, null)
            }

        })
    }
}