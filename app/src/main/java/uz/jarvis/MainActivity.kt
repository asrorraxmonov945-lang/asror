package uz.jarvis

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private lateinit var log: TextView
    private lateinit var scroll: ScrollView
    private lateinit var status: TextView
    private lateinit var commands: Commands
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var recognizer: SpeechRecognizer? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        commands = Commands(this)
        buildUi()
        tts = TextToSpeech(this, this)
        askPermissions()
        addLog("Jarvis: Salom! Mikrofon tugmasini bosib gapiring. Masalan: \"Telegramdan oxirgi xabar kim yozdi\", \"Dadamga qoʻngʻiroq qil\", \"YouTube ni och\", \"Soat necha\".")
    }

    private fun buildUi() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#060D17"))
        root.setPadding(dp(16), dp(24), dp(16), dp(16))

        val title = TextView(this)
        title.text = "J A R V I S"
        title.setTextColor(Color.parseColor("#38D4FF"))
        title.textSize = 22f
        title.typeface = Typeface.DEFAULT_BOLD
        title.gravity = Gravity.CENTER
        root.addView(title)

        status = TextView(this)
        status.setTextColor(Color.parseColor("#7F9BB5"))
        status.gravity = Gravity.CENTER
        status.text = "Tayyor"
        root.addView(status)

        scroll = ScrollView(this)
        log = TextView(this)
        log.setTextColor(Color.parseColor("#DFF3FF"))
        log.textSize = 16f
        log.setPadding(dp(4), dp(12), dp(4), dp(12))
        scroll.addView(log)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val mic = Button(this)
        mic.text = "🎤  Gapirish"
        mic.textSize = 20f
        mic.setTextColor(Color.parseColor("#04202B"))
        mic.setBackgroundColor(Color.parseColor("#38D4FF"))
        mic.setOnClickListener { listen() }
        root.addView(mic, LinearLayout.LayoutParams(-1, dp(72)))

        val notif = Button(this)
        notif.text = "🔔 Xabarlarni oʻqishga ruxsat"
        notif.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        root.addView(notif, LinearLayout.LayoutParams(-1, -2))

        val perm = Button(this)
        perm.text = "Ruxsatlar (mikrofon, kontaktlar, qoʻngʻiroq)"
        perm.setOnClickListener { askPermissions() }
        root.addView(perm, LinearLayout.LayoutParams(-1, -2))

        setContentView(root)
    }

    private fun askPermissions() {
        val need = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE
        ).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (need.isNotEmpty()) requestPermissions(need.toTypedArray(), 1)
    }

    override fun onInit(code: Int) {
        if (code != TextToSpeech.SUCCESS) return
        val t = tts ?: return
        for (loc in listOf(Locale("uz", "UZ"), Locale("ru", "RU"), Locale.getDefault())) {
            val r = t.setLanguage(loc)
            if (r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED) {
                ttsReady = true
                break
            }
        }
    }

    private fun addLog(s: String) {
        log.append(s + "\n\n")
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun speak(s: String) {
        addLog("Jarvis: $s")
        if (ttsReady) tts?.speak(s, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
    }

    private fun listen() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            askPermissions()
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            speak("Telefonda ovozni tanish xizmati topilmadi. Google ilovasi oʻrnatilganini tekshiring.")
            return
        }
        tts?.stop()
        recognizer?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { status.text = "Tinglayapman…" }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { status.text = "Tayyor" }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onError(error: Int) {
                status.text = "Tayyor"
                if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) askPermissions()
                else speak("Eshitmadim, qayta urinib koʻring. (xato kodi $error)")
            }
            override fun onResults(results: Bundle?) {
                status.text = "Tayyor"
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (text.isNullOrBlank()) {
                    speak("Eshitmadim.")
                    return
                }
                addLog("Siz: $text")
                speak(commands.handle(text))
            }
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "uz-UZ")
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        r.startListening(i)
    }

    override fun onDestroy() {
        recognizer?.destroy()
        tts?.shutdown()
        super.onDestroy()
    }
}
