package com.github.importantamigo.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.LinearLayout
import com.aliucord.api.SettingsAPI
import com.aliucord.utils.DimenUtils.dp
import com.aliucord.views.Button
import com.aliucord.views.TextInput
import com.aliucord.widgets.BottomSheet
import com.github.importantamigo.BestSilentTyping


class ColorPickerSheet(private val settings: SettingsAPI) : BottomSheet() {
    private var isUpdating = false

    @SuppressLint("SetTextI18n", "DefaultLocale")
    override fun onViewCreated(view: View, bundle: Bundle?) {
        super.onViewCreated(view, bundle)

        val ctx = view.context

        val preview = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(40.dp, 40.dp).apply {
                leftMargin = 16.dp
            }
            val currentColor = settings.getInt("iconColor", Color.RED)
            setBackgroundColor(currentColor)
        }

        lateinit var hexInput: TextInput

        val picker = RadialColorPicker(ctx).apply {
            val size = 200.dp
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                topMargin = 16.dp
                bottomMargin = 16.dp
            }

            val currentColor = settings.getInt("iconColor", Color.RED)
            setColor(currentColor)

            onColorChanged = { color ->
                if (!isUpdating) {
                    isUpdating = true
                    hexInput.editText.setText(String.format("#%06X", 0xFFFFFF and color))
                    isUpdating = false
                }
                settings.setInt("iconColor", color)
                preview.setBackgroundColor(color)
                BestSilentTyping.promptUpdate?.invoke()
            }
        }

        val initialColor = settings.getInt("iconColor", Color.RED)
        hexInput = TextInput(
            ctx, "Hex Color", String.format("#%06X", 0xFFFFFF and initialColor),
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                /* This version doesn't have toColorInt, so I have to do this */

                @SuppressLint("UseKtx")
                override fun afterTextChanged(s: Editable?) {
                    if (isUpdating) return
                    val hex = s?.toString() ?: return
                    try {
                        val color = Color.parseColor(if (hex.startsWith("#")) hex else "#$hex")
                        isUpdating = true
                        picker.setColor(color)
                        preview.setBackgroundColor(color)
                        settings.setInt("iconColor", color)
                        BestSilentTyping.promptUpdate?.invoke()
                    } catch (_ : Exception) {
                    } finally {
                        isUpdating = false
                    }
                }
            },
        ).apply {
            layoutParams = LinearLayout.LayoutParams(160.dp, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 16.dp
            }
            addView(hexInput)
            addView(preview)
        }

        addView(picker)
        addView(row)

        Button(ctx).apply {
            text = "Reset to Default"
            setOnClickListener {
                isUpdating = true
                settings.remove("iconColor")
                val defaultColor = Color.RED
                picker.setColor(defaultColor)
                preview.setBackgroundColor(defaultColor)
                hexInput.editText.setText(String.format("#%06X", 0xFFFFFF and defaultColor))
                BestSilentTyping.promptUpdate?.invoke()
                isUpdating = false
                dismiss()
            }
        }.also { addView(it) }
    }
}
