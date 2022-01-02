package com.scaredeer.opengl

import android.opengl.GLSurfaceView
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    companion object {
        private val TAG = MainActivity::class.simpleName
    }

    private lateinit var glSurfaceView: GLSurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.v(TAG, "onCreate")

        glSurfaceView = GLSurfaceView(this)
        glSurfaceView.setEGLContextClientVersion(2)
        glSurfaceView.setRenderer(Renderer())

        setContentView(glSurfaceView)
    }

    override fun onResume() {
        super.onResume()
        Log.v(TAG, "onResume")
        glSurfaceView.onResume()
    }

    override fun onPause() {
        Log.v(TAG, "onPause")
        glSurfaceView.onPause()
        super.onPause()
    }
}