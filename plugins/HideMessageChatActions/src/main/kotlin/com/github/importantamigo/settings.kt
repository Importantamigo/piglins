package com.github.importantamigo

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.aliucord.Utils
import com.aliucord.api.SettingsAPI
import com.aliucord.fragments.SettingsPage
import com.discord.views.CheckedSetting
import com.lytefast.flexinput.R

class HideMessageChatActionsSettings : SettingsPage() {

    companion object {
        lateinit var settings: SettingsAPI

        val discoveredActions = mutableMapOf<String, String>()
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, bundle: Bundle?) {
        super.onViewCreated(view, bundle)

        setActionBarTitle("HideMessageChatActions")
        val ctx = requireContext()

        if (discoveredActions.isEmpty()) {
            HideMessageChatActions.defaultActions.keys.forEach { label ->
                discoveredActions[label] = label
            }
        }
        if (discoveredActions.values.none { it.endsWith("(Plugin)") }) {
            addView(
                TextView(ctx, null, 0, R.i.UiKit_TextView).apply {
                    text = "Open the message action sheet once to discover plugin actions"
                },
            )
        }

        discoveredActions.entries
            .sortedWith(
                compareBy(
                    { it.key.endsWith("(Plugin)") },
                    { it.key },
                ),
            )
            .forEach { (displayLabel, key) ->
                Utils.createCheckedSetting(ctx, CheckedSetting.ViewType.CHECK, displayLabel, null).apply {
                    setOnCheckedListener { checked -> settings.setBool(key, checked) }
                    isChecked = settings.getBool(key, false)
                }.also { linearLayout.addView(it) }
            }
    }
}
