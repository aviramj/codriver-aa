package com.aviramj.codriver

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.aviramj.codriver.ai.AnthropicClient
import com.aviramj.codriver.ai.ChatMessage
import com.aviramj.codriver.ai.Models
import com.aviramj.codriver.ai.OpenAiClient
import com.aviramj.codriver.audio.Speaker
import com.aviramj.codriver.car.CarChatService
import com.aviramj.codriver.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Phone companion. Holds the configuration and launches Driving Mode (the
 * full-screen, hands-light assistant). "Send test & speak" confirms the key,
 * model, and voice work without driving.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val modelLabels: List<String> = Models.PRESETS.map { it.label } + "Custom (type a model id)…"
    private val customIndex: Int get() = Models.PRESETS.size

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupModelSpinner()

        with(binding) {
            drivingMode.setOnClickListener {
                startActivity(Intent(this@MainActivity, DrivingModeActivity::class.java))
            }
            carChat.setOnClickListener { setUpCarChat() }

            baseUrl.setText(Settings.baseUrl(this@MainActivity))
            apiKey.setText(Settings.apiKey(this@MainActivity))
            anthropicKey.setText(Settings.anthropicApiKey(this@MainActivity))
            transcribeModel.setText(Settings.transcribeModel(this@MainActivity))
            systemPrompt.setText(Settings.systemPrompt(this@MainActivity))
            ttsOpenAi.isChecked = Settings.useOpenAiTts(this@MainActivity)
            ttsModel.setText(Settings.ttsModel(this@MainActivity))
            ttsVoice.setText(Settings.ttsVoice(this@MainActivity))

            save.setOnClickListener {
                persist()
                Toast.makeText(this@MainActivity, "Saved", Toast.LENGTH_SHORT).show()
            }
            test.setOnClickListener { runTest() }
        }

        val saved = Settings.chatModel(this)
        val presetIdx = Models.indexOfId(saved)
        if (presetIdx >= 0) {
            binding.modelSpinner.setSelection(presetIdx)
        } else {
            binding.customModel.setText(saved)
            binding.modelSpinner.setSelection(customIndex)
        }

        Speaker.warmUp(this)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
        }
    }

    private fun setupModelSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, modelLabels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.modelSpinner.adapter = adapter
        binding.modelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                binding.customModel.visibility = if (position == customIndex) View.VISIBLE else View.GONE
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun selectedModel(): String {
        val pos = binding.modelSpinner.selectedItemPosition
        return if (pos in Models.PRESETS.indices) {
            Models.PRESETS[pos].id
        } else {
            binding.customModel.text.toString().trim().ifEmpty { Settings.DEFAULT_CHAT_MODEL }
        }
    }

    private fun persist() {
        with(binding) {
            Settings.save(
                this@MainActivity,
                baseUrl.text.toString(),
                apiKey.text.toString(),
                anthropicKey.text.toString(),
                selectedModel(),
                transcribeModel.text.toString(),
                systemPrompt.text.toString(),
                ttsOpenAi.isChecked,
                ttsModel.text.toString(),
                ttsVoice.text.toString(),
            )
        }
    }

    private fun runTest() {
        persist()
        val model = Settings.chatModel(this)
        val prompt = binding.testMessage.text.toString()
            .ifBlank { "Say hello in one sentence and confirm you're working." }
        binding.testResult.text = "Sending…"

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    if (Models.isAnthropic(model)) {
                        val key = Settings.anthropicApiKey(this@MainActivity)
                        if (key.isBlank()) return@runCatching "ERROR: add your Anthropic API key to use Claude models."
                        AnthropicClient(key).chat(
                            Settings.systemPrompt(this@MainActivity),
                            model,
                            listOf(ChatMessage("user", prompt)),
                        )
                    } else {
                        val key = Settings.apiKey(this@MainActivity)
                        if (key.isBlank()) return@runCatching "ERROR: add your OpenAI API key."
                        OpenAiClient(Settings.baseUrl(this@MainActivity), key).chat(
                            Settings.systemPrompt(this@MainActivity),
                            model,
                            listOf(ChatMessage("user", prompt)),
                        )
                    }
                }.getOrElse { "ERROR: ${it.message ?: it}" }
            }
            binding.testResult.text = result
            if (!result.startsWith("ERROR")) {
                Speaker.speak(this@MainActivity, result)
            }
        }
    }

    /**
     * Starts the watcher service that posts a co:driver greeting the moment the
     * phone connects to Android Auto (its messaging surface only shows messages
     * that arrive while connected). Needs notification permission on Android 13+.
     */
    private fun setUpCarChat() {
        persist()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
            return
        }
        CarChatService.start(this)
        Toast.makeText(
            this,
            "co:driver is ready for Android Auto. Connect your car, open co:driver under Messages, then reply by voice.",
            Toast.LENGTH_LONG,
        ).show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_NOTIF && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            setUpCarChat()
        }
    }

    private companion object {
        const val REQ_MIC = 1
        const val REQ_NOTIF = 2
    }
}
