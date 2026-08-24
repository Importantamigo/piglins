package com.github.importantamigo

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.res.ResourcesCompat
import com.aliucord.Utils
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.api.CommandsAPI
import com.aliucord.entities.Plugin
import com.aliucord.patcher.after
import com.aliucord.patcher.before
import com.aliucord.utils.DimenUtils.dp
import com.aliucord.utils.ReflectUtils
import com.discord.api.commands.ApplicationCommandType
import com.discord.stores.StoreUserTyping
import com.discord.widgets.chat.input.MessageDraftsRepo
import com.discord.widgets.chat.input.WidgetChatInputEditText
import com.lytefast.flexinput.widget.FlexEditText

@AliucordPlugin
class BestSilentTyping : Plugin() {
    companion object {
        var promptUpdate: (() -> Unit)? = null
    }

    private var keyboardOn: Drawable? = null
    private var keyboardOff: Drawable? = null
    private var currentIconView: ImageView? = null

    init {
        settingsTab = SettingsTab(BestSilentTypingSettings::class.java, SettingsTab.Type.PAGE)
    }

    override fun start(context: Context) {
        promptUpdate = { updateIcon() }
        BestSilentTypingSettings.pluginSettings = settings
        val res = resources ?: return

        @Suppress("DiscouragedApi")
        keyboardOn = ResourcesCompat.getDrawable(
            res,
            res.getIdentifier("ic_keyboard_outlined", "drawable", "com.github.importantamigo"),
            null
        )
        @Suppress("DiscouragedApi")
        keyboardOff = ResourcesCompat.getDrawable(
            res,
            res.getIdentifier("ic_keyboard_off_outlined", "drawable", "com.github.importantamigo"),
            null
        )

        commands.registerCommand(
            "silenttyping",
            "Toggle silent typing",
            listOf(
                Utils.createCommandOption(
                    ApplicationCommandType.BOOLEAN,
                    "enabled",
                    "Whether to enable silent typing",
                ),
            ),
        ) { ctx ->
            val newValue = if (ctx.containsArg("enabled")) {
                ctx.getRequiredBool("enabled")
            } else {
                !settings.getBool("silentTyping", false)
            }
            settings.setBool("silentTyping", newValue)
            updateIcon()
            CommandsAPI.CommandResult("Silent typing is now ${if (newValue) "enabled" else "disabled"}", null, false)
        }

        patcher.before<StoreUserTyping>(
            "setUserTyping",
            Long::class.javaPrimitiveType ?: Long::class.java,
        ) { param ->
            if (settings.getBool("silentTyping", false)) {
                param.result = null
            }
        }

        patcher.after<WidgetChatInputEditText>(
            FlexEditText::class.java,
            MessageDraftsRepo::class.java
        ) {
            if (!settings.getBool("showToggle", true)) return@after

            val et = try {
                ReflectUtils.getField(this, "editText") as? FlexEditText
            } catch (e: Exception) {
                null
            } ?: return@after

            val group = et.parent as? LinearLayout ?: return@after

            if (group.findViewWithTag<View>("silent_typing_toggle") != null) return@after

            val ctx = group.context
            val iconView = ImageView(ctx).apply {
                tag = "silent_typing_toggle"
                val size = 24.dp
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setMargins(0, 0, 4.dp, 0)
                }
                
                setOnClickListener {
                    val isSilent = settings.getBool("silentTyping", false)
                    settings.setBool("silentTyping", !isSilent)
                    updateIcon()
                }
                
                val color = settings.getInt("iconColor", -1)
                if (color != -1) setColorFilter(color)
            }
            
            currentIconView = iconView
            updateIcon()
            
            val index = if (group.childCount > 1) 1 else group.childCount
            group.addView(iconView, index)
        }
    }

    override fun stop(context: Context) {
        patcher.unpatchAll()
        currentIconView = null
    }

    private fun updateIcon() {
        Utils.mainThread.post {
            currentIconView?.let { view ->
                view.setImageDrawable(if (settings.getBool("silentTyping", false)) keyboardOff else keyboardOn)
                val color = settings.getInt("iconColor", -1)
                if (color != -1) view.setColorFilter(color) else view.clearColorFilter()
            }
        }
    }
}
