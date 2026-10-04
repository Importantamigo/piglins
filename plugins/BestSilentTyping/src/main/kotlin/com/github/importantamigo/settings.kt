package com.github.importantamigo

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import com.aliucord.Utils
import com.aliucord.api.SettingsAPI
import com.aliucord.fragments.SettingsPage
import com.aliucord.views.Button
import com.discord.views.CheckedSetting
import com.github.importantamigo.ui.ColorPickerSheet

class BestSilentTypingSettings : SettingsPage() {

    companion object {
        lateinit var pluginSettings: SettingsAPI
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, bundle: Bundle?) {
        super.onViewCreated(view, bundle)

        val ctx = view.context
        setActionBarTitle("BestSilentTyping")

        Utils.createCheckedSetting(
            ctx,
            CheckedSetting.ViewType.SWITCH,
            "Silent Typing",
            "Prevent others from seeing when you are typing"
        ).apply {
            isChecked = pluginSettings.getBool("silentTyping", false)
            setOnCheckedListener {
                pluginSettings.setBool("silentTyping", it)
                BestSilentTyping.notifyToggle(pluginSettings.getBool("showToast", false), it)
            }
        }.also { linearLayout.addView(it) }

        Utils.createCheckedSetting(
            ctx,
            CheckedSetting.ViewType.SWITCH,
            "Show Chatbox Toggle",
            "Show a toggle icon in the chat input area"
        ).apply {
            isChecked = pluginSettings.getBool("showToggle", true)
            setOnCheckedListener {
                pluginSettings.setBool("showToggle", it)
            }
        }.also { linearLayout.addView(it) }

        Utils.createCheckedSetting(
            ctx,
            CheckedSetting.ViewType.SWITCH,
            "Show Toast on Toggle",
            "Show a toast notification when silent typing is toggled"
        ).apply {
            isChecked = pluginSettings.getBool("showToast", false)
            setOnCheckedListener {
                pluginSettings.setBool("showToast", it)
            }
        }.also { linearLayout.addView(it) }

        Button(ctx).apply {
            text = "Pick Icon Color"
            setOnClickListener {
                ColorPickerSheet(pluginSettings).show(parentFragmentManager, "color-picker")
            }
        }.also { linearLayout.addView(it) }
    }
}
