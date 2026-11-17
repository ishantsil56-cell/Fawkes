package com.ishantsil.fawkes.commands

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.ishantsil.fawkes.actions.AlarmTimerAction
import com.ishantsil.fawkes.actions.CalculatorAction
import com.ishantsil.fawkes.actions.CallAction
import com.ishantsil.fawkes.actions.FlashlightAction
import com.ishantsil.fawkes.actions.InfoAction
import com.ishantsil.fawkes.actions.MusicAction
import com.ishantsil.fawkes.actions.OpenAppAction
import com.ishantsil.fawkes.actions.SmsAction
import com.ishantsil.fawkes.actions.SosAction
import com.ishantsil.fawkes.actions.SystemPanelAction
import com.ishantsil.fawkes.actions.VolumeAction
import com.ishantsil.fawkes.actions.WhatsAppAction
import com.ishantsil.fawkes.data.AutomationStore
import com.ishantsil.fawkes.data.NotesStore
import com.ishantsil.fawkes.data.ReminderStore
import com.ishantsil.fawkes.voice.LlmManager

/**
 * Runs matched commands. Work happens on a background thread; onResult
 * (spoken confirmation, log line) is always posted to the main thread.
 */
object CommandExecutor {

    private val mainHandler = Handler(Looper.getMainLooper())

    fun executePhrase(
        context: Context,
        phrase: String,
        onResult: (spoken: String, log: String) -> Unit,
    ) {
        val result = CommandMatcher.match(phrase, context)
        execute(context, result, onResult)
    }

    fun execute(
        context: Context,
        result: CommandMatcher.MatchResult,
        onResult: (spoken: String, log: String) -> Unit,
    ) {
        Thread {
            try {
                when (result) {
                    is CommandMatcher.MatchResult.Flashlight -> {
                        val msg = FlashlightAction.setEnabled(context, result.on)
                        post(onResult, msg, result.describe())
                    }
                    is CommandMatcher.MatchResult.OpenApp -> {
                        val msg = OpenAppAction.open(context, result.appName)
                        post(onResult, msg, result.describe())
                    }
                    is CommandMatcher.MatchResult.OpenLastApp -> {
                        val pkg = ConversationMemory.lastAppPackage
                        val name = ConversationMemory.lastAppName
                        if (pkg != null && name != null) {
                            val msg = OpenAppAction.openPackage(context, pkg, name)
                            post(onResult, msg, "open $name again")
                        } else {
                            post(onResult, "I don't know which app you mean yet.", result.describe())
                        }
                    }
                    is CommandMatcher.MatchResult.SetAlarm -> {
                        val msg = AlarmTimerAction.setAlarm(context, result.hour, result.minute)
                        post(onResult, msg, result.describe())
                    }
                    is CommandMatcher.MatchResult.SetTimer -> {
                        val msg = AlarmTimerAction.setTimer(context, result.seconds)
                        post(onResult, msg, result.describe())
                    }
                    is CommandMatcher.MatchResult.Call -> {
                        val msg = CallAction.call(context, result.name)
                        post(onResult, msg, result.describe())
                    }
                    is CommandMatcher.MatchResult.CallLastContact -> {
                        val name = ConversationMemory.lastContactName
                        val number = ConversationMemory.lastContactNumber
                        if (name != null && number != null) {
                            val msg = CallAction.callNumber(context, name, number)
                            post(onResult, msg, "call $name")
                        } else {
                            post(onResult, "I don't know who you mean yet.", result.describe())
                        }
                    }
                    is CommandMatcher.MatchResult.AskTime -> {
                        val msg = InfoAction.timeNow()
                        post(onResult, msg, result.describe())
                    }
                    is CommandMatcher.MatchResult.AskDate -> {
                        val msg = InfoAction.dateToday()
                        post(onResult, msg, result.describe())
                    }
                    is CommandMatcher.MatchResult.AskBattery -> {
                        val msg = InfoAction.batteryLevel(context)
                        post(onResult, msg, result.describe())
                    }
                    is CommandMatcher.MatchResult.Calculate -> {
                        val answer = CalculatorAction.evaluate(result.expression)
                        if (answer != null) {
                            post(onResult, answer, result.describe())
                        } else {
                            post(onResult, "I couldn't work that out.", result.describe())
                        }
                    }
                    is CommandMatcher.MatchResult.AddNote -> {
                        NotesStore.add(context, result.text)
                        post(onResult, "Noted.", result.describe())
                    }
                    is CommandMatcher.MatchResult.ReadNotes -> {
                        val notes = NotesStore.list(context)
                        if (notes.isEmpty()) {
                            post(onResult, "You have no notes.", result.describe())
                        } else {
                            val spoken = notes.take(3).joinToString(". ")
                            val log = "Notes (${notes.size}):\n" +
                                notes.take(10).joinToString("\n") { "- $it" }
                            post(onResult, spoken, log)
                        }
                    }
                    is CommandMatcher.MatchResult.ClearNotes -> {
                        NotesStore.clear(context)
                        post(onResult, "Notes cleared.", result.describe())
                    }
                    is CommandMatcher.MatchResult.Volume -> {
                        val msg = VolumeAction.volume(context, result.action)
                        post(onResult, msg, result.describe())
                    }
                    is CommandMatcher.MatchResult.Brightness -> {
                        val msg = VolumeAction.brightness(context, result.action)
                        post(onResult, msg, result.describe())
                    }
                    is CommandMatcher.MatchResult.OpenWifiPanel -> {
                        val msg = SystemPanelAction.openWifi(context)
                        post(onResult, msg, result.describe())
                    }
                    is CommandMatcher.MatchResult.OpenBluetoothPanel -> {
                        val msg = SystemPanelAction.openBluetooth(context)
                        post(onResult, msg, result.describe())
                    }
                    is CommandMatcher.MatchResult.SendSms -> {
                        val msg = SmsAction.send(context, result.name, result.message)
                        post(onResult, msg, result.describe())
                    }
                    is CommandMatcher.MatchResult.SendWhatsApp -> {
                        val msg = WhatsAppAction.send(context, result.name, result.message)
                        post(onResult, msg, result.describe())
                    }
                    is CommandMatcher.MatchResult.SetReminder -> {
                        val msg = ReminderStore.addAndSchedule(context, result.text, result.triggerAt)
                        post(onResult, msg, result.describe())
                    }
                    is CommandMatcher.MatchResult.Music -> {
                        val msg = MusicAction.control(context, result.action)
                        post(onResult, msg, result.describe())
                    }
                    is CommandMatcher.MatchResult.Sos -> {
                        val msg = if (result.start) {
                            SosAction.start(context)
                        } else {
                            SosAction.stop(context)
                        }
                        post(onResult, msg, result.describe())
                    }
                    is CommandMatcher.MatchResult.DefineAutomation -> {
                        AutomationStore.save(
                            context, result.kind, result.trigger, result.actions,
                        )
                        val kindWord = if (result.kind == AutomationStore.KIND_ROUTINE) {
                            "routine"
                        } else {
                            "custom command"
                        }
                        post(
                            onResult,
                            "Done. When you say ${result.trigger}, I'll run ${result.actions.size} steps.",
                            "Saved $kindWord \"${result.trigger}\"",
                        )
                    }
                    is CommandMatcher.MatchResult.RunAutomation -> {
                        runAutomation(context, result.trigger, onResult)
                    }
                    is CommandMatcher.MatchResult.ListCustoms -> {
                        val items = AutomationStore.list(context, AutomationStore.KIND_CUSTOM)
                        if (items.isEmpty()) {
                            post(onResult, "You have no custom commands yet.", result.describe())
                        } else {
                            val log = "Custom commands:\n" + items.joinToString("\n") {
                                "- ${it.trigger} (${it.actions.size} steps)"
                            }
                            post(onResult, "${items.size} custom commands. Check the log.", log)
                        }
                    }
                    is CommandMatcher.MatchResult.ListRoutines -> {
                        val items = AutomationStore.list(context, AutomationStore.KIND_ROUTINE)
                        if (items.isEmpty()) {
                            post(onResult, "You have no routines yet.", result.describe())
                        } else {
                            val log = "Routines:\n" + items.joinToString("\n") {
                                "- ${it.trigger} (${it.actions.size} steps)"
                            }
                            post(onResult, "${items.size} routines. Check the log.", log)
                        }
                    }
                    is CommandMatcher.MatchResult.DeleteAutomation -> {
                        val deleted = AutomationStore.delete(context, result.trigger)
                        if (deleted) {
                            post(onResult, "Deleted ${result.trigger}.", result.describe())
                        } else {
                            post(onResult, "I couldn't find ${result.trigger}.", result.describe())
                        }
                    }
                    is CommandMatcher.MatchResult.AskLlm -> {
                        // Async: LlmManager calls back on the main thread.
                        LlmManager.ask(context, result.question,
                            onAnswer = { answer ->
                                post(onResult, answer, "Asked: ${result.question}")
                            },
                            onError = { error ->
                                post(onResult, error, result.describe())
                            },
                        )
                    }
                    is CommandMatcher.MatchResult.Unknown -> {
                        post(
                            onResult,
                            "I don't know that one yet.",
                            "Unknown: \"${result.phrase}\"",
                        )
                    }
                }
            } catch (e: Exception) {
                post(
                    onResult,
                    "Something went wrong: ${e.message}",
                    "Error running ${result.describe()}: ${e.message}",
                )
            }
        }.start()
    }

    /** Runs an automation's steps in order, chaining the async results. */
    private fun runAutomation(
        context: Context,
        trigger: String,
        onResult: (String, String) -> Unit,
    ) {
        val automation = AutomationStore.find(context, trigger)
        if (automation == null) {
            post(onResult, "I couldn't find $trigger.", trigger)
            return
        }
        val spokenParts = mutableListOf<String>()
        fun runNext(index: Int) {
            if (index >= automation.actions.size) {
                val spoken = spokenParts.joinToString(". ").ifBlank { "Done." }
                post(onResult, spoken, "Ran ${automation.trigger} (${automation.actions.size} steps)")
                return
            }
            val step = automation.actions[index]
            // No nesting: a step naming another automation is skipped.
            if (AutomationStore.find(context, CommandMatcher.normalise(step)) != null) {
                spokenParts.add("skipped a nested command")
                runNext(index + 1)
                return
            }
            executePhrase(context, step) { spoken, _ ->
                spokenParts.add(spoken)
                runNext(index + 1)
            }
        }
        runNext(0)
    }

    private fun post(
        onResult: (String, String) -> Unit,
        spoken: String,
        log: String,
    ) {
        mainHandler.post { onResult(spoken, log) }
    }
}
