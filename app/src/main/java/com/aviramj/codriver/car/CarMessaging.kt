package com.aviramj.codriver.car

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.getSystemService
import com.aviramj.codriver.R
import com.aviramj.codriver.ai.ChatMessage
import org.json.JSONArray
import org.json.JSONObject

/**
 * Android Auto integration via the messaging pattern.
 *
 * The Car App Library has no "AI assistant" category, but Android Auto has
 * first-class support for messaging apps: it reads incoming messages aloud and
 * captures a spoken reply. So we model the assistant as a contact you text -
 * each AI answer is posted as a [NotificationCompat.MessagingStyle] notification
 * (which the car reads aloud), and the reply action carries a [RemoteInput] so
 * the car hands us your spoken message. [MessageReplyReceiver] turns that into
 * an AI call and posts the answer as the next message.
 *
 * The thread is persisted so it survives the process being recycled between the
 * reply broadcast and the next launch.
 */
object CarMessaging {
    const val CHANNEL_ID = "codriver_car_chat"
    private const val NOTIFICATION_ID = 42
    const val KEY_TEXT_REPLY = "codriver_reply"
    const val ACTION_REPLY = "com.aviramj.codriver.action.REPLY"
    const val ACTION_MARK_READ = "com.aviramj.codriver.action.MARK_READ"

    private const val PREFS = "codriver_car_chat"
    private const val KEY_HISTORY = "history"
    private const val KEY_LAST_AUTO_GREET = "last_auto_greet"
    private const val MAX_MESSAGES = 20
    private const val THREAD_SHOWN = 10
    private const val AUTO_GREET_DEBOUNCE_MS = 5 * 60 * 1000L

    private val assistant: Person = Person.Builder().setName("co:driver").setKey("codriver-ai").build()
    private val me: Person = Person.Builder().setName("You").setKey("me").build()

    private data class CarMsg(val role: String, val content: String, val ts: Long)

    // ---- persistence ----

    private fun prefs(c: Context) =
        c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun load(c: Context): MutableList<CarMsg> {
        val raw = prefs(c).getString(KEY_HISTORY, null) ?: return mutableListOf()
        return runCatching {
            val arr = JSONArray(raw)
            MutableList(arr.length()) {
                val o = arr.getJSONObject(it)
                CarMsg(o.getString("role"), o.getString("content"), o.optLong("ts"))
            }
        }.getOrDefault(mutableListOf())
    }

    private fun save(c: Context, list: List<CarMsg>) {
        val arr = JSONArray()
        list.takeLast(MAX_MESSAGES).forEach {
            arr.put(JSONObject().put("role", it.role).put("content", it.content).put("ts", it.ts))
        }
        prefs(c).edit().putString(KEY_HISTORY, arr.toString()).apply()
    }

    private fun append(c: Context, role: String, content: String): List<CarMsg> {
        val list = load(c)
        list.add(CarMsg(role, content, System.currentTimeMillis()))
        val trimmed = list.takeLast(MAX_MESSAGES)
        save(c, trimmed)
        return trimmed
    }

    fun reset(c: Context) = prefs(c).edit().remove(KEY_HISTORY).apply()

    /** The conversation as chat turns for the AI request, trimmed to start on a user turn. */
    fun aiHistory(c: Context): List<ChatMessage> =
        load(c).map { ChatMessage(it.role, it.content) }.dropWhile { it.role != "user" }

    /** Record the user's (spoken) message in the thread. */
    fun addUserMessage(c: Context, text: String) {
        append(c, "user", text)
    }

    /** Append an assistant message and (re)post the Android Auto notification. */
    fun postAssistantMessage(c: Context, text: String) {
        notify(c, append(c, "assistant", text))
    }

    /** Start a fresh conversation with a greeting so it appears in Android Auto. */
    fun start(c: Context) {
        reset(c)
        postAssistantMessage(c, "Hi, I'm co:driver. Reply to this message to ask me anything while you drive.")
    }

    /**
     * Auto-greet when the car connects (called from [CarBluetoothReceiver]).
     * Debounced so a flurry of Bluetooth (re)connects at the start of a drive
     * posts the greeting only once.
     */
    fun startFromCarConnect(c: Context) {
        val p = prefs(c)
        val now = System.currentTimeMillis()
        if (now - p.getLong(KEY_LAST_AUTO_GREET, 0L) < AUTO_GREET_DEBOUNCE_MS) return
        p.edit().putLong(KEY_LAST_AUTO_GREET, now).apply()
        start(c)
    }

    // ---- notification ----

    fun ensureChannel(c: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = c.getSystemService<NotificationManager>() ?: return
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Car chat", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "The co:driver assistant conversation shown on Android Auto."
                }
            )
        }
    }

    private fun notify(c: Context, thread: List<CarMsg>) {
        ensureChannel(c)
        val mgr = c.getSystemService<NotificationManager>() ?: return

        val style = NotificationCompat.MessagingStyle(me).setConversationTitle("co:driver")
        thread.takeLast(THREAD_SHOWN).forEach { msg ->
            // A null sender marks a message as the local user's; messages from the
            // assistant carry its Person so Android Auto reads them aloud.
            val sender = if (msg.role == "assistant") assistant else null
            style.addMessage(msg.content, msg.ts, sender)
        }

        val notification = NotificationCompat.Builder(c, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mic)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setShowWhen(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(false)
            .addAction(replyAction(c))
            .addInvisibleAction(markReadAction(c))
            .build()

        mgr.notify(NOTIFICATION_ID, notification)
    }

    private fun pendingBroadcast(c: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(c, MessageReplyReceiver::class.java).setAction(action)
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags = flags or PendingIntent.FLAG_MUTABLE
        return PendingIntent.getBroadcast(c, requestCode, intent, flags)
    }

    private fun replyAction(c: Context): NotificationCompat.Action {
        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY).setLabel("Message co:driver").build()
        return NotificationCompat.Action.Builder(R.drawable.ic_mic, "Reply", pendingBroadcast(c, ACTION_REPLY, 1))
            .addRemoteInput(remoteInput)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .setAllowGeneratedReplies(true)
            .build()
    }

    private fun markReadAction(c: Context): NotificationCompat.Action =
        NotificationCompat.Action.Builder(R.drawable.ic_mic, "Mark as read", pendingBroadcast(c, ACTION_MARK_READ, 2))
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .build()
}
