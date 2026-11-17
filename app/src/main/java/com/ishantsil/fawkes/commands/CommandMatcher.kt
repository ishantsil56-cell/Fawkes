package com.ishantsil.fawkes.commands

import android.content.Context
import com.ishantsil.fawkes.data.AutomationStore
import java.util.Calendar

/**
 * Rule-based command matcher. Patterns run in priority order: the user's own
 * automations first, then exact command phrases, then keyword contains.
 * Anything unmatched is reported as unknown and logged — the log becomes
 * the next feature list.
 */
object CommandMatcher {

    private val fillerWords = setOf("please", "hey", "fawkes", "ok", "okay")

    sealed class MatchResult {
        abstract fun describe(): String

        data class Flashlight(val on: Boolean) : MatchResult() {
            override fun describe() = if (on) "flashlight on" else "flashlight off"
        }

        data class OpenApp(val appName: String) : MatchResult() {
            override fun describe() = "open $appName"
        }

        object OpenLastApp : MatchResult() {
            override fun describe() = "open it again"
        }

        data class SetAlarm(val hour: Int, val minute: Int) : MatchResult() {
            override fun describe() = "alarm at %d:%02d".format(hour, minute)
        }

        data class SetTimer(val seconds: Int) : MatchResult() {
            override fun describe() = "timer for $seconds seconds"
        }

        data class Call(val name: String) : MatchResult() {
            override fun describe() = "call $name"
        }

        object CallLastContact : MatchResult() {
            override fun describe() = "call them"
        }

        object AskTime : MatchResult() {
            override fun describe() = "what time is it"
        }

        object AskDate : MatchResult() {
            override fun describe() = "what is the date"
        }

        object AskBattery : MatchResult() {
            override fun describe() = "battery level"
        }

        data class Calculate(val expression: String) : MatchResult() {
            override fun describe() = expression
        }

        data class AddNote(val text: String) : MatchResult() {
            override fun describe() = "note: $text"
        }

        object ReadNotes : MatchResult() {
            override fun describe() = "read my notes"
        }

        object ClearNotes : MatchResult() {
            override fun describe() = "clear notes"
        }

        data class Volume(val action: String) : MatchResult() {
            override fun describe() = "volume $action"
        }

        data class Brightness(val action: String) : MatchResult() {
            override fun describe() = "brightness $action"
        }

        object OpenWifiPanel : MatchResult() {
            override fun describe() = "open Wi-Fi settings"
        }

        object OpenBluetoothPanel : MatchResult() {
            override fun describe() = "open Bluetooth settings"
        }

        /** name null means the last contact ("text him ..."). */
        data class SendSms(val name: String?, val message: String) : MatchResult() {
            override fun describe() = "text ${name ?: "them"}: $message"
        }

        data class SendWhatsApp(val name: String?, val message: String) : MatchResult() {
            override fun describe() = "whatsapp ${name ?: "them"}: $message"
        }

        data class SetReminder(val text: String, val triggerAt: Long) : MatchResult() {
            override fun describe() = "remind: $text"
        }

        data class Music(val action: String) : MatchResult() {
            override fun describe() = "music $action"
        }

        data class Sos(val start: Boolean) : MatchResult() {
            override fun describe() = if (start) "sos flashlight" else "stop sos"
        }

        data class DefineAutomation(
            val kind: String,
            val trigger: String,
            val actions: List<String>,
        ) : MatchResult() {
            override fun describe() = "create $kind $trigger"
        }

        data class RunAutomation(val trigger: String) : MatchResult() {
            override fun describe() = trigger
        }

        object ListCustoms : MatchResult() {
            override fun describe() = "list custom commands"
        }

        object ListRoutines : MatchResult() {
            override fun describe() = "list routines"
        }

        data class DeleteAutomation(val trigger: String) : MatchResult() {
            override fun describe() = "delete $trigger"
        }

        data class AskLlm(val question: String) : MatchResult() {
            override fun describe() = "ask: $question"
        }

        data class Unknown(val phrase: String) : MatchResult() {
            override fun describe() = "unknown command (\"$phrase\")"
        }
    }

    fun normalise(raw: String): String {
        return raw.lowercase()
            .split(" ")
            .filter { it.isNotBlank() && it !in fillerWords }
            .joinToString(" ")
            .trim()
    }

    fun match(raw: String, context: Context): MatchResult =
        matchInternal(normalise(raw), context, allowAsk = true)

    private fun matchInternal(phrase: String, context: Context, allowAsk: Boolean): MatchResult {
        if (phrase.isBlank()) return MatchResult.Unknown(phrase)

        // 0. The user's own automations win over built-ins.
        AutomationStore.find(context, phrase)?.let {
            return MatchResult.RunAutomation(it.trigger)
        }

        // 1. Define a custom command or routine.
        defineAutomation(phrase)?.let { return it }

        // 2. List / delete automations.
        when (phrase) {
            "list custom commands", "my custom commands", "custom commands" ->
                return MatchResult.ListCustoms
            "list routines", "my routines", "routines" ->
                return MatchResult.ListRoutines
        }
        if (phrase.startsWith("delete custom command ") || phrase.startsWith("delete routine ")) {
            val trigger = phrase.removePrefix("delete custom command ")
                .removePrefix("delete routine ").trim()
            if (trigger.isNotBlank()) return MatchResult.DeleteAutomation(trigger)
        }

        // 3. "Ask ..." — try the question as a command first, else the on-device AI.
        if (allowAsk && phrase.startsWith("ask ")) {
            val inner = matchInternal(phrase.removePrefix("ask ").trim(), context, allowAsk = false)
            return if (inner is MatchResult.Unknown) {
                MatchResult.AskLlm(inner.phrase)
            } else {
                inner
            }
        }

        // 4. SOS flashlight.
        if (phrase == "sos" || phrase == "sos flashlight" || phrase == "flash sos" ||
            phrase == "send sos"
        ) {
            return MatchResult.Sos(true)
        }
        if (phrase == "stop sos" || phrase == "sos off" || phrase == "cancel sos") {
            return MatchResult.Sos(false)
        }

        // 5. Flashlight / torch (English + Hindi).
        if (phrase.contains("flashlight") || phrase.contains("torch") || phrase.contains("टॉर्च")) {
            val onWords = listOf(" on", "on karo", "चालू", "on kar", "jalao", "जलाओ")
            val offWords = listOf(" off", "off karo", "बंद", "off kar", "bujhao", "बुझाओ")
            if (onWords.any { phrase.contains(it) }) return MatchResult.Flashlight(true)
            if (offWords.any { phrase.contains(it) }) return MatchResult.Flashlight(false)
            return MatchResult.Unknown(phrase)
        }

        // 6. Reminders.
        if (phrase.startsWith("remind me ")) {
            parseReminder(phrase)?.let { return it }
        }

        // 7. Music control.
        when (phrase) {
            "play", "play music", "resume", "resume music" -> return MatchResult.Music("play")
            "pause", "pause music", "stop music" -> return MatchResult.Music("pause")
            "next", "next song", "skip", "skip song" -> return MatchResult.Music("next")
            "previous", "previous song", "last song" -> return MatchResult.Music("previous")
        }

        // 8. Alarm.
        if (phrase.contains("alarm") || phrase.startsWith("wake me")) {
            parseClockTime(phrase)?.let { (hour, minute) ->
                return MatchResult.SetAlarm(hour, minute)
            }
        }

        // 9. Timer.
        if (phrase.contains("timer")) {
            parseDurationSeconds(phrase)?.let { return MatchResult.SetTimer(it) }
        }

        // 10. Calls.
        if (phrase.startsWith("call ") || phrase.startsWith("phone ")) {
            val name = phrase.removePrefix("call ").removePrefix("phone ").trim()
            if (ConversationMemory.isContactPronoun(name)) {
                return MatchResult.CallLastContact
            }
            if (name.isNotBlank()) return MatchResult.Call(name)
        }

        // 11. SMS.
        if (phrase.startsWith("text ") || phrase.startsWith("message ") || phrase.startsWith("sms ")) {
            val rest = phrase.removePrefix("text ").removePrefix("message ")
                .removePrefix("sms ").trim()
            parseNameAndMessage(rest)?.let { (name, message) ->
                return MatchResult.SendSms(name, message)
            }
        }

        // 12. WhatsApp.
        if (phrase.startsWith("whatsapp ")) {
            val rest = phrase.removePrefix("whatsapp ").trim()
            parseNameAndMessage(rest)?.let { (name, message) ->
                return MatchResult.SendWhatsApp(name, message)
            }
        }

        // 13. Open app.
        if (phrase == "open it again" || phrase == "open that again") {
            return MatchResult.OpenLastApp
        }
        if (phrase.startsWith("open ") || phrase.startsWith("launch ")) {
            val name = phrase.removePrefix("open ").removePrefix("launch ").trim()
            if (name == "wifi" || name == "wi-fi" || name == "wi-fi settings" ||
                name == "wifi settings"
            ) {
                return MatchResult.OpenWifiPanel
            }
            if (name == "bluetooth" || name == "bluetooth settings") {
                return MatchResult.OpenBluetoothPanel
            }
            if (name.isNotBlank()) return MatchResult.OpenApp(name)
        }

        // 14. Time / date / battery (English + Hindi).
        if (phrase.contains("what time") || phrase == "time" || phrase.contains("समय")) {
            return MatchResult.AskTime
        }
        if (phrase.contains("what day") || phrase.contains("what is the date") ||
            phrase.contains("today's date") || phrase == "date"
        ) {
            return MatchResult.AskDate
        }
        if (phrase.contains("battery") || phrase.contains("बैटरी")) {
            return MatchResult.AskBattery
        }

        // 15. Calculator.
        if (isCalculation(phrase)) return MatchResult.Calculate(phrase)

        // 16. Notes.
        if (phrase == "read my notes" || phrase == "read notes" || phrase == "my notes") {
            return MatchResult.ReadNotes
        }
        if (phrase == "clear notes" || phrase == "delete all notes" || phrase == "delete my notes") {
            return MatchResult.ClearNotes
        }
        if (phrase.startsWith("note ") || phrase.startsWith("take a note ") ||
            phrase.startsWith("remember ")
        ) {
            val text = phrase.removePrefix("note ").removePrefix("take a note ")
                .removePrefix("remember ").trim().removePrefix(":").trim()
            if (text.isNotBlank()) return MatchResult.AddNote(text)
        }

        // 17. Volume / brightness.
        if (phrase.contains("volume")) {
            when {
                phrase.contains("up") -> return MatchResult.Volume("up")
                phrase.contains("down") -> return MatchResult.Volume("down")
                phrase.contains("mute") -> return MatchResult.Volume("mute")
                phrase.contains("unmute") -> return MatchResult.Volume("unmute")
                phrase.contains("max") -> return MatchResult.Volume("max")
                phrase.contains("min") -> return MatchResult.Volume("min")
            }
        }
        if (phrase.contains("brightness")) {
            when {
                phrase.contains("up") -> return MatchResult.Brightness("up")
                phrase.contains("down") -> return MatchResult.Brightness("down")
                phrase.contains("max") -> return MatchResult.Brightness("max")
                phrase.contains("min") -> return MatchResult.Brightness("min")
            }
        }

        // 18. Settings panels.
        if (phrase.contains("wi-fi") || phrase.contains("wifi")) {
            return MatchResult.OpenWifiPanel
        }
        if (phrase.contains("bluetooth")) {
            return MatchResult.OpenBluetoothPanel
        }

        return MatchResult.Unknown(phrase)
    }

    // ---- definition parsing ----

    /**
     * "create custom command good night runs flashlight off and set alarm for 7 am"
     * "create routine good morning runs flashlight on then open youtube"
     * "when i say movie time runs volume down and brightness down"
     */
    private fun defineAutomation(phrase: String): MatchResult.DefineAutomation? {
        val kind: String
        val rest: String
        when {
            phrase.startsWith("create custom command ") -> {
                kind = AutomationStore.KIND_CUSTOM
                rest = phrase.removePrefix("create custom command ").trim()
            }
            phrase.startsWith("create routine ") -> {
                kind = AutomationStore.KIND_ROUTINE
                rest = phrase.removePrefix("create routine ").trim()
            }
            phrase.startsWith("when i say ") -> {
                kind = AutomationStore.KIND_CUSTOM
                rest = phrase.removePrefix("when i say ").trim()
            }
            else -> return null
        }
        val separator = listOf(" runs ", " does ", " then do ").firstOrNull { rest.contains(it) }
            ?: return null
        val trigger = rest.substringBefore(separator).trim()
        val actionsPart = rest.substringAfter(separator).trim()
        if (trigger.isBlank() || actionsPart.isBlank()) return null
        val actions = actionsPart
            .split(" and ", " then ", ", ")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(6)
        if (actions.isEmpty()) return null
        return MatchResult.DefineAutomation(kind, trigger, actions)
    }

    // ---- reminder parsing ----

    private fun parseReminder(phrase: String): MatchResult.SetReminder? {
        val now = System.currentTimeMillis()
        // "remind me in 30 minutes to drink water"
        val durationMatch = Regex("""in (\d+)\s*(seconds?|minutes?|hours?)""").find(phrase)
        if (durationMatch != null) {
            val amount = durationMatch.groupValues[1].toIntOrNull() ?: return null
            val unitSeconds = when {
                durationMatch.groupValues[2].startsWith("hour") -> 3600L
                durationMatch.groupValues[2].startsWith("minute") -> 60L
                else -> 1L
            }
            val triggerAt = now + amount * unitSeconds * 1000L
            var text = phrase.substringAfter(durationMatch.value).trim()
                .removePrefix("to ").trim()
            if (text.isBlank()) text = "Reminder"
            return MatchResult.SetReminder(text, triggerAt)
        }
        // "remind me at 7 pm to call maa"
        parseClockTime(phrase)?.let { (hour, minute) ->
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, minute)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, 1)
            val timeMatch = Regex("""(\d{1,2})(?::(\d{2}))?\s*(am|pm)?""").find(phrase)
            var text = if (timeMatch != null) {
                phrase.substringAfter(timeMatch.value).trim().removePrefix("to ").trim()
            } else {
                ""
            }
            if (text.isBlank()) text = "Reminder"
            return MatchResult.SetReminder(text, cal.timeInMillis)
        }
        return null
    }

    // ---- time helpers ----

    /** Parses "7 am", "7:30 pm", "19:30" into 24-hour (hour, minute). */
    fun parseClockTime(phrase: String): Pair<Int, Int>? {
        val match = Regex("""(\d{1,2})(?::(\d{2}))?\s*(am|pm)?""").find(phrase) ?: return null
        var hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: 0
        val meridiem = match.groupValues[3]
        if (hour > 23 || minute > 59) return null
        if (meridiem == "pm" && hour < 12) hour += 12
        if (meridiem == "am" && hour == 12) hour = 0
        return hour to minute
    }

    /** Parses "10 minutes", "2 hours", "30 seconds" into seconds. */
    fun parseDurationSeconds(phrase: String): Int? {
        val match = Regex("""(\d+)\s*(seconds?|minutes?|hours?)""").find(phrase) ?: return null
        val amount = match.groupValues[1].toIntOrNull() ?: return null
        return when {
            match.groupValues[2].startsWith("hour") -> amount * 3600
            match.groupValues[2].startsWith("minute") -> amount * 60
            else -> amount
        }
    }

    /** Splits "rahul on my way" into ("rahul", "on my way"). Name is one word. */
    private fun parseNameAndMessage(rest: String): Pair<String?, String>? {
        if (rest.isBlank()) return null
        val firstSpace = rest.indexOf(' ')
        if (firstSpace == -1) return null
        val first = rest.substring(0, firstSpace).trim()
        val message = rest.substring(firstSpace + 1).trim().removePrefix(":").trim()
        if (message.isBlank()) return null
        if (first in setOf("him", "her", "them")) return null to message
        return first to message
    }

    private fun isCalculation(phrase: String): Boolean {
        if (phrase.startsWith("what is ") || phrase.startsWith("calculate ") ||
            phrase.startsWith("compute ")
        ) {
            return phrase.any { it.isDigit() }
        }
        return Regex("""\d+\s*(plus|minus|times|multiplied by|divided by|\+|-|\*|x|/)\s*\d+""")
            .containsMatchIn(phrase)
    }
}
