package com.emuji.emuji

import android.util.Log
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

class OAuthServer {
    private var serverSocket: ServerSocket? = null

    fun start() {
        Log.e(TAG, "Starting Auth Server")
        try {
            serverSocket = ServerSocket(PORT)
            Log.d(TAG, "Server started at port $PORT")

            while (true) {
                val clientSocket: Socket = serverSocket!!.accept()
                Log.d(TAG, "Client connected: $clientSocket")

                val `in` = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
                val out = PrintWriter(clientSocket.getOutputStream(), true)

                val requestContent = StringBuilder() // To store request content

                var line: String?
                while (`in`.readLine().also { line = it } != null && line!!.isNotEmpty()) {
                    requestContent.append(line).append("\n")
                }

                val request = requestContent.toString()
                Log.d(TAG, "Received request:\n$request") // Print the received request content

                // Rest of your code to process the request remains unchanged...
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun stop() {
        try {
            if (serverSocket != null && !serverSocket!!.isClosed) {
                serverSocket!!.close()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    companion object {
        private const val TAG = "OAuthServer"
        private const val PORT = 8888
    }
}
