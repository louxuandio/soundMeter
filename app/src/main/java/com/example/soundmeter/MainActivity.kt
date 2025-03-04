package com.example.soundmeter

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.soundmeter.ui.theme.SoundMeterTheme

class MainActivity : ComponentActivity() {

    private lateinit var audioRecord: AudioRecord
    private var bufferSize: Int = 0
    private var isRecording by mutableStateOf(false)


    // reference: https://github.com/albertopasqualetto/SoundMeterESP.git
    companion object {
        val TAG = MainActivity::class.simpleName
        val REQUEST_CODE = 1234

        private val PROGRESS_BAR_HEIGHT = 50.dp
        private val PROGRESS_BAR_WIDTH = 200.dp

        private var isRunning = false   // used instead of MeterService.isRecording to prevent race conditions

        var coldStart = true

        fun dBToProgress(dB : Float) : Float {
            return dB/120 // scale from [0dB-120dB] to [0-1]
        }
    }

    private val _db = mutableStateOf("Waiting...")
    private val _progress = mutableStateOf(0.0f)
    val db: State<String> get() = _db
    val progress: State<Float> get() = _progress

    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    // reference: https://www.quora.com/How-do-you-develop-an-Android-app-that-measures-sound-levels
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //ActivityCompat.requestPermissions()

        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT

        bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        val sampleRate = 44100 // Sample rate in Hz
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)


        setContent {
            SoundMeterTheme {
                SoundScreen(db = db.value, progress = progress.value)

                 fun calculateSoundLevel(buffer: ShortArray, readSize: Int): Float {
                    var sum = 0.0
                    for (i in 0 until readSize) {
                        sum += buffer[i] * buffer[i]
                    }
                    val rms = Math.sqrt(sum / readSize)
                    return (20 * Math.log10(rms)).toFloat()
                }

                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_CODE)
                }else {
                    initAudioRecordAndStartRecording()
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // permitted
                initAudioRecordAndStartRecording()
            } else {
                Toast.makeText(this, "Recording permission is denied to use this feature", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun initAudioRecordAndStartRecording(){
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("AudioRecord", "Missing RECORD_AUDIO privilege.")
            return
        }

        bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e("AudioRecord", "Unable to get the right buffer size")
            return
        }
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            Log.e("AudioRecord", "AudioRecord Initialization failure")
            return
        }
        isRecording = true
        Thread {
            audioRecord.startRecording()
            val buffer = ShortArray(bufferSize)
            while (isRecording) {
                val readSize = audioRecord.read(buffer, 0, buffer.size)
                if (readSize > 0) {
                    val soundLevel = calculateSoundLevel(buffer, readSize)
                    // update UI
                    runOnUiThread {
                        _db.value = String.format("%.1f dB", soundLevel)
                        _progress.value = dBToProgress(soundLevel)
                    }
                }
            }
            audioRecord.stop()
        }.start()
    }

    private fun calculateSoundLevel(buffer: ShortArray, readSize: Int): Float {
        var sum = 0.0
        for (i in 0 until readSize) {
            sum += buffer[i] * buffer[i]
        }
        val rms = Math.sqrt(sum / readSize)
        // The sample rate is 1，here we calculate dB = 20 * log10(rms)
        return (20 * Math.log10(rms)).toFloat()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRecording = false  // stop recording
    }
}


@Composable
fun SoundScreen(db: String, progress: Float){
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ){
        Column(
            modifier = Modifier
                .padding(16.dp)
                .wrapContentSize()
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Sound Meter: $db", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = progress.coerceIn(0f, 1f),
                modifier = Modifier
                    .width(200.dp)
                    .height(50.dp)
            )
            if (db.toFloat()>50){
                Text("The noise level exceeds the threshold", color = Color.Red)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun Preview(){
    SoundMeterTheme {
        SoundScreen(db = "60 dB", progress = 0.5f)
    }
}
