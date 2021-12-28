package com.scaredeer.opengl

import android.opengl.GLSurfaceView
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    companion object {
        private val TAG = MainActivity::class.simpleName
    }

    private var mGLSurfaceView: GLSurfaceView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.v(TAG, "onCreate")

        mGLSurfaceView = GLSurfaceView(this)
        mGLSurfaceView!!.setEGLContextClientVersion(2)
        mGLSurfaceView!!.setRenderer(Renderer())

        setContentView(mGLSurfaceView)
    }

    override fun onDestroy() {
        Log.v(TAG, "onDestroy")
        mGLSurfaceView = null
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        Log.v(TAG, "onResume")
        mGLSurfaceView!!.onResume()
    }

    override fun onPause() {
        Log.v(TAG, "onPause")
        mGLSurfaceView!!.onPause()
        super.onPause()
    }
}