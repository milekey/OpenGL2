package com.scaredeer.opengl

import android.opengl.GLES20.*
import android.opengl.GLSurfaceView
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * ゲームのメインループに相当するクラス
 * （もちろん、画面の更新を中心としたもので、ゲームモデルの論理的なループとは必ずしも同じではないが、
 * 実用上はこのクラスを中心に構成していいと思う）
 *
 * MainActivity で implement して記述を統合することも全然可能だが、
 * MainActivity はその他の UI のコードなども盛り込まれることになるので、コードの見通しが悪くなり、
 * あまり実用的ではないので、素直に分離している。
 */
class Renderer : GLSurfaceView.Renderer {

    companion object {
        private val TAG = Renderer::class.simpleName

        private const val BYTES_PER_FLOAT = 4 // Java float is 32-bit = 4-byte
        private const val POSITION_COMPONENT_COUNT = 2 // x, y（※ z は常に 0 なので省略）

        // STRIDE は要するに、次の頂点データセットへスキップするバイト数を表したもの。
        // 一つの頂点データセットは頂点座標 x, y の 2 つで構成されているが、
        // 次の頂点を処理する際に、2 つ分のバイト数をスキップする必要が生じる。
        private const val STRIDE = POSITION_COMPONENT_COUNT * BYTES_PER_FLOAT

        private val TILE = floatArrayOf(
            // x, y
            -0.5f, -0.5f, // 左下
            -0.5f, 0.5f,  // 左上
            0.5f, -0.5f,  // 右下
            0.5f, 0.5f    // 右上
        )

        private const val A_POSITION = "a_Position"

        private const val VERTEX_SHADER = """
            attribute vec4 $A_POSITION;
            void main() {
                gl_Position = $A_POSITION;
            }       
        """

        private const val U_COLOR = "u_Color"

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec4 $U_COLOR;
            void main() {
                gl_FragColor = $U_COLOR;
            }
        """

        /**
         * Compiles a shader, returning the OpenGL object ID.
         *
         * @see <a href="https://media.pragprog.com/titles/kbogla/code/AirHockey1/src/com/airhockey/android/util/ShaderHelper.java">OpenGL ES 2 for Android</a>
         * @param [type]       GL_VERTEX_SHADER or GL_FRAGMENT_SHADER
         * @param [shaderCode] String data of shader code
         * @return the OpenGL object ID (or 0 if compilation failed)
         */
        private fun compileShader(type: Int, shaderCode: String): Int {
            // Create a new shader object.
            val shaderObjectId = glCreateShader(type)
            if (shaderObjectId == 0) {
                Log.w(TAG, "Could not create new shader.")
                return 0
            }

            // Pass in (upload) the shader source.
            glShaderSource(shaderObjectId, shaderCode)

            // Compile the shader.
            glCompileShader(shaderObjectId)

            // Get the compilation status.
            val compileStatus = IntArray(1)
            glGetShaderiv(shaderObjectId, GL_COMPILE_STATUS, compileStatus, 0)

            // Print the shader info log to the Android log output.
            Log.v(TAG, """
            Result of compiling source:
                $shaderCode
            Log:
                ${glGetShaderInfoLog(shaderObjectId)}
            """.trimIndent())

            // Verify the compile status.
            if (compileStatus[0] == 0) {
                // If it failed, delete the shader object.
                glDeleteShader(shaderObjectId)
                Log.w(TAG, "Compilation of shader failed.")
                return 0
            }

            // Return the shader object ID.
            return shaderObjectId
        }

        /**
         * Links a vertex shader and a fragment shader together into an OpenGL
         * program. Returns the OpenGL program object ID, or 0 if linking failed.
         *
         * @see <a href="https://media.pragprog.com/titles/kbogla/code/AirHockey1/src/com/airhockey/android/util/ShaderHelper.java">OpenGL ES 2 for Android</a>
         * @param [vertexShaderId]   OpenGL object ID of vertex shader
         * @param [fragmentShaderId] OpenGL object ID of fragment shader
         * @return OpenGL program object ID (or 0 if linking failed)
         */
        private fun linkProgram(vertexShaderId: Int, fragmentShaderId: Int): Int {
            // Create a new program object.
            val programObjectId = glCreateProgram()
            if (programObjectId == 0) {
                Log.w(TAG, "Could not create new program")
                return 0
            }

            // Attach the vertex shader to the program.
            glAttachShader(programObjectId, vertexShaderId)
            // Attach the fragment shader to the program.
            glAttachShader(programObjectId, fragmentShaderId)

            // Link the two shaders together into a program.
            glLinkProgram(programObjectId)

            // Get the link status.
            val linkStatus = IntArray(1)
            glGetProgramiv(programObjectId, GL_LINK_STATUS, linkStatus, 0)

            // Print the program info log to the Android log output.
            Log.v(TAG, """
                Result log of linking program:
                ${glGetProgramInfoLog(programObjectId)}
            """.trimIndent())

            // Verify the link status.
            if (linkStatus[0] == 0) {
                // If it failed, delete the program object.
                glDeleteProgram(programObjectId)
                Log.w(TAG, "Linking of program failed.")
                return 0
            }

            // Return the program object ID.
            return programObjectId
        }

        /**
         * Validates an OpenGL program. Should only be called when developing the application.
         *
         * @see <a href="https://media.pragprog.com/titles/kbogla/code/AirHockey1/src/com/airhockey/android/util/ShaderHelper.java">OpenGL ES 2 for Android</a>
         * @param [programObjectId] OpenGL program object ID to validate
         * @return boolean
         */
        private fun validateProgram(programObjectId: Int): Boolean {
            glValidateProgram(programObjectId)
            val validateStatus = IntArray(1)
            glGetProgramiv(programObjectId, GL_VALIDATE_STATUS, validateStatus, 0)
            Log.v(TAG, """
                Result status code of validating program: ${validateStatus[0]}
                Log:
                ${glGetProgramInfoLog(programObjectId)}
            """.trimIndent())
            return validateStatus[0] != 0
        }
    }

    // JavaVM (float) -> DirectBuffer (FloatBuffer) -> OpenGL
    private val vertexData: FloatBuffer = ByteBuffer
        .allocateDirect(TILE.size * BYTES_PER_FLOAT)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .put(TILE)

    private var aPosition = 0
    private var uColor = 0

    override fun onSurfaceCreated(gl10: GL10, eglConfig: EGLConfig) {
        Log.v(TAG, "onSurfaceCreated")
        glClearColor(0f, 0f, 0f, 0f)

        // Compile the shaders.
        val vertexShader = compileShader(GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragmentShader = compileShader(GL_FRAGMENT_SHADER, FRAGMENT_SHADER)

        // Link them into a shader program.
        val program = linkProgram(vertexShader, fragmentShader)
        validateProgram(program)

        // Use this program.
        glUseProgram(program)

        // Retrieve uniform locations for the shader program.
        uColor = glGetUniformLocation(program, U_COLOR)

        // Retrieve attribute locations for the shader program.
        aPosition = glGetAttribLocation(program, A_POSITION)
    }

    override fun onSurfaceChanged(gl10: GL10, width: Int, height: Int) {
        Log.v(TAG, "onSurfaceChanged")
        // Set the OpenGL viewport to fill the entire surface.
        glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl10: GL10) {
        // Clear the rendering surface.
        glClear(GL_COLOR_BUFFER_BIT)

        glUniform4f(uColor, 1f, 0f, 0f, 1f) // 赤色

        // Bind our data, specified by the variable vertexData, to the vertex
        // attribute at location of A_POSITION.
        vertexData.position(0)
        glVertexAttribPointer(
            aPosition,
            POSITION_COMPONENT_COUNT,
            GL_FLOAT,
            false,
            STRIDE,
            vertexData
        )
        glEnableVertexAttribArray(aPosition)

        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4) // N 字形の順に描く
    }
}