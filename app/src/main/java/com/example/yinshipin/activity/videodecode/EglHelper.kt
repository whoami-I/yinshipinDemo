package com.example.yinshipin.activity.videodecode

import android.opengl.*
import android.opengl.EGL14.*
import android.util.Log
import android.view.Surface
import javax.microedition.khronos.egl.EGL10.EGL_ALPHA_SIZE

/**
 * 配置egl环境的步骤：
 * 1.获取默认的EGLDisplay。
 * 2.对EGLDisplay进行初始化。
 * 3.输入预设置的参数获取EGL支持的EGLConfig。
 * 4.通过EGLDisplay和EGLConfig创建一个EGLContext上下文环境。
 * 5.创建一个EGLSurface来连接EGL和设备的屏幕。
 * 6.在渲染线程绑定EGLSurface和EGLContext，这样可以在该线程使用opengl相关的指令来进行渲染
 * 7.【进行OpenGL ES的API渲染步骤】(与EGL无关)
 * 8.调用SwapBuffer进行双缓冲切换显示渲染画面。
 * 9.释放EGL相关资源EGLSurface、EGLContext、EGLDisplay。
 */
class EglHelper constructor(surface: Surface) {
    var eglGetDisplay: EGLDisplay? = null
    var eglContext: EGLContext? = null
    var surface: Surface = surface
    var eglSurface: EGLSurface? = null
    var mEglConfig: EGLConfig? = null
    fun initEnv() {
        eglGetDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglGetDisplay == EGL14.EGL_NO_DISPLAY) throw RuntimeException("no default display")
        val versionArray = IntArray(2)
        val eglInitialize = EGL14.eglInitialize(eglGetDisplay, versionArray, 0, versionArray, 1)
        checkEGLError("eglInitialize")
        if (!eglInitialize) {
            throw  RuntimeException("Unable to initialize EGL14")
        }
        Log.d("TAG", "major version:${versionArray[0]}  minor version:${versionArray[1]}")
        val configAttributes: IntArray = intArrayOf(
            EGL_BUFFER_SIZE, 32,   //颜色缓冲区中所有组成颜色的位数
            EGL_ALPHA_SIZE, 8,     //颜色缓冲区中透明度位数
            EGL_BLUE_SIZE, 8,      //颜色缓冲区中蓝色位数
            EGL_GREEN_SIZE, 8,     //颜色缓冲区中绿色位数
            EGL_RED_SIZE, 8,       //颜色缓冲区中红色位数
            EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,  //渲染窗口支持的布局组成
            EGL_SURFACE_TYPE, EGL_WINDOW_BIT,  //EGL 窗口支持的类型
            EGL_NONE
        )
        var eglConfig: Array<EGLConfig?> = arrayOfNulls<EGLConfig>(1)
        var configs = IntArray(1)
        if (!EGL14.eglChooseConfig(
                eglGetDisplay, configAttributes, 0, eglConfig,
                0, eglConfig.size, configs, 0
            )
        ) {
            throw  RuntimeException("eglChooseConfig failed")
        }
        checkEGLError("eglChooseConfig")
        //如果没有配置的Config
        if (configs[0] < 0) {
            throw RuntimeException("Unable to find any matching EGL config")
        }
        if (eglConfig[0] == null)
            throw RuntimeException("eglChooseConfig returned null")

        mEglConfig = eglConfig[0]
        //指定OpenGL ES2版本
        val contextAttributes = intArrayOf(EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE)
        //创建EGLContext上下文,share_context表示可以与EGLContext共享资源
        eglContext = EGL14.eglCreateContext(
            eglGetDisplay,
            eglConfig[0],
            EGL14.EGL_NO_CONTEXT,
            contextAttributes,
            0
        )
        checkEGLError("eglCreateContext")
        //需要检测Context是否存在
        if (eglContext === EGL_NO_CONTEXT) {
            throw java.lang.RuntimeException("Failed to create EGL context")
        }
    }

    fun createSurface() {
        //创建可显示的Surface

        val surfaceAttribs = intArrayOf(EGL_NONE)

        eglSurface =
            EGL14.eglCreateWindowSurface(eglGetDisplay, mEglConfig, surface, surfaceAttribs, 0)
        if (eglSurface === EGL_NO_SURFACE) {
            throw java.lang.RuntimeException("Failed to create window surface")
        }
        checkEGLError("eglCreateWindowSurface")

        if (!EGL14.eglMakeCurrent(eglGetDisplay, eglSurface, eglSurface, eglContext)) {
            throw  RuntimeException("detachCurrent failed")
        }
        checkEGLError("eglMakeCurrent")
    }

    fun setPresentationTimeNs(nsecs: Long) {
        // 设置发动给EGL的时间间隔
        EGLExt.eglPresentationTimeANDROID(eglGetDisplay, eglSurface, nsecs)
        checkEGLError("eglPresentationTimeANDROID")
    }

    //释放资源
    fun release() {
        eglGetDisplay?.also {
            //解绑
            eglMakeCurrent(eglGetDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
            eglSurface?.also { eglDestroySurface(eglGetDisplay, eglSurface) }
            eglContext?.also { eglDestroyContext(eglGetDisplay, eglContext) }
            eglTerminate(eglGetDisplay)
            eglGetDisplay = null
            eglSurface = null
            eglContext = null
        }
    }

    fun swap(): Int {
        if (eglGetDisplay != null && eglSurface != null) {
            return if (!EGL14.eglSwapBuffers(eglGetDisplay, eglSurface)) {
                EGL14.eglGetError()
            } else EGL14.EGL_SUCCESS
        } else {
            return EGL14.EGL_SUCCESS
        }
    }

    fun checkEGLError(msg: String) {
        val eglError = EGL14.eglGetError()
        if (eglError != EGL14.EGL_SUCCESS) {
            throw RuntimeException("$msg egl error:$eglError")
        }
    }
}