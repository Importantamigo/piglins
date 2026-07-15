package com.github.importantamigo

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.aliucord.Utils
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.entities.Plugin
import com.aliucord.patcher.after
import com.discord.widgets.chat.list.actions.WidgetChatListActions

@AliucordPlugin
class HideMessageChatActions : Plugin() {

    companion object {
        val defaultActions = mapOf(
            "Resend" to "dialog_chat_actions_resend",
            "Edit" to "dialog_chat_actions_edit",
            "Reply" to "dialog_chat_actions_reply",
            "Create Thread" to "dialog_chat_actions_start_thread",
            "Copy Text" to "dialog_chat_actions_copy",
            "Delete" to "dialog_chat_actions_delete",
            "Profile" to "dialog_chat_actions_profile",
            "Manage Reactions" to "dialog_chat_actions_manage_reactions",
            "Remove All Reactions" to "dialog_chat_actions_remove_all_reactions",
            "Publish" to "dialog_chat_actions_publish",
            "Pin" to "dialog_chat_actions_pin",
            "Share" to "dialog_chat_actions_share",
            "Mark Unread" to "dialog_chat_actions_mark_unread",
            "Copy ID" to "dialog_chat_actions_copy_id",
            "Report" to "dialog_chat_actions_report",
        )
    }

    init {
        settingsTab = SettingsTab(HideMessageChatActionsSettings::class.java, SettingsTab.Type.PAGE)
    }

    override fun start(context: Context) {
        HideMessageChatActionsSettings.settings = settings

        defaultActions.forEach { (display, _) ->
            HideMessageChatActionsSettings.discoveredActions[display] = display
        }

        patcher.after<WidgetChatListActions>(
            "configureUI",
            WidgetChatListActions.Model::class.java,
        ) { param ->
            val widget = param.thisObject as WidgetChatListActions
            val root = widget.requireView() as ViewGroup

            root.viewTreeObserver.addOnGlobalLayoutListener(
                object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        root.viewTreeObserver.removeOnGlobalLayoutListener(this)

                        defaultActions.forEach { (display, resName) ->
                            val resId = Utils.getResId(resName, "id")
                            if (resId != 0) {
                                root.findViewById<View>(resId)
                                    ?.takeIf { settings.getBool(display, false) }?.visibility = View.GONE
                            }
                        }

                        val containerId = Utils.getResId("dialog_chat_actions_container", "id")
                        val container = root.findViewById<ViewGroup>(containerId) ?: return
                        for (i in 0 until container.childCount) {
                            val child = container.getChildAt(i)
                            val label = extractText(child)?.trim() ?: continue
                            if (label.isEmpty()) continue

                            val isDefault = label in defaultActions
                            if (!isDefault) {
                                val displayLabel = "$label (Plugin)"
                                HideMessageChatActionsSettings.discoveredActions[displayLabel] = label
                                child.takeIf { settings.getBool(label, false) }?.visibility = View.GONE
                            }
                        }
                    }
                },
            )
        }
    }

    override fun stop(context: Context) {
        patcher.unpatchAll()
    }

    private fun extractText(v: View): String? = when (v) {
        is TextView -> v.text?.toString()
        is ViewGroup -> {
            var text: String? = null
            for (i in 0 until v.childCount) {
                text = extractText(v.getChildAt(i))
                if (!text.isNullOrEmpty()) break
            }
            text
        }

        else -> null
    }
}
