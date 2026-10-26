package com.plitka.launcher.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import com.plitka.launcher.data.Prefs
import com.plitka.launcher.data.Tile
import com.plitka.launcher.data.TileStore
import com.plitka.launcher.util.applySystemBarPadding
import com.plitka.launcher.util.dpi
import com.plitka.launcher.util.isDefaultHome
import com.plitka.launcher.util.requestDefaultHome
import com.plitka.launcher.util.transparentSystemBars

class SettingsActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        transparentSystemBars()
        window.setBackgroundDrawable(ColorDrawable(Color.parseColor("#0B0B0D")))
        prefs = Prefs(this)

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(24), dpi(32), dpi(24), dpi(32))
            setBackgroundColor(Color.parseColor("#0B0B0D"))
        }

        root.addView(title("Настройки"))

        root.addView(section("Сетка"))
        val cols = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (n in 3..4) {
            cols.addView(button("$n колонки") {
                prefs.columns = n
                recreate()
            }, weight())
        }
        root.addView(cols)

        root.addView(section("Скругление плиток"))
        root.addView(slider(0, 30, prefs.radiusDp) { prefs.radiusDp = it })

        root.addView(section("Непрозрачность"))
        root.addView(slider(20, 100, prefs.opacity) { prefs.opacity = it })

        root.addView(section("Внешний вид плиток"))
        root.addView(
            switchRow(
                "Названия приложений",
                "Подписи под иконками",
                prefs.showLabels
            ) { checked -> prefs.showLabels = checked }
        )

        root.addView(
            switchRow(
                "Верхняя панель",
                "Время, батарея и значки уведомлений",
                !prefs.hideStatusBar
            ) { checked -> prefs.hideStatusBar = !checked }
        )

        root.addView(section("Акцент"))
        val palette = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (c in Tile.PALETTE) {
            val dot = View(this).apply {
                setBackgroundColor(c)
                setOnClickListener { paintAll(c) }
            }
            palette.addView(dot, LinearLayout.LayoutParams(dpi(38), dpi(38)).apply {
                marginEnd = dpi(8)
            })
        }
        root.addView(palette)

        root.addView(section("Система"))
        root.addView(
            body(
                if (isDefaultHome()) "«Плитка» уже выбрана домашним экраном."
                else "Сейчас домашний экран — другое приложение."
            )
        )
        root.addView(button("Сделать домашним экраном") { requestDefaultHome() })

        root.addView(section("Плитки"))
        root.addView(body("Зажми плитку и потяни, чтобы поменять порядок.\nПодержи плитку над другой — получится папка."))
        root.addView(button("Сбросить раскладку") {
            TileStore.reset(this)
            finish()
        })

        root.addView(section("О приложении"))
        root.addView(body("Плитка 0.3 — плиточный рабочий стол, написанный вручную.\nАвтор: владелец устройства.\nБез рекламы, без Google-сервисов, без интернета."))

        val scroll = ScrollView(this).apply {
            clipToPadding = false
            addView(root)
        }
        scroll.applySystemBarPadding()
        setContentView(scroll)
    }

    private fun paintAll(color: Int) {
        val tiles = TileStore.loadOrCreate(this)
        tiles.forEach { it.color = color }
        TileStore.save(this, tiles)
        prefs.accent = color
        finish()
    }

    private fun title(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextColor(Color.WHITE)
        textSize = 28f
        setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL))
        setPadding(0, 0, 0, dpi(16))
    }

    private fun section(text: String): TextView = TextView(this).apply {
        this.text = text.uppercase()
        setTextColor(Color.parseColor("#8A8A8E"))
        textSize = 12f
        letterSpacing = 0.12f
        setPadding(0, dpi(22), 0, dpi(10))
    }

    private fun body(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextColor(Color.parseColor("#C9C9CE"))
        textSize = 14f
        setLineSpacing(dpi(4).toFloat(), 1f)
    }

    private fun button(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    /** Строка «заголовок + пояснение + переключатель»: нажимается целиком, а не только рычажок. */
    private fun switchRow(
        title: String,
        subtitle: String,
        checked: Boolean,
        onChange: (Boolean) -> Unit
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dpi(6), 0, dpi(6))
        }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(this).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 17f
        })
        texts.addView(TextView(this).apply {
            text = subtitle
            setTextColor(Color.parseColor("#8A8A8E"))
            textSize = 13f
            setPadding(0, dpi(4), 0, 0)
        })
        val sw = Switch(this).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, c -> onChange(c) }
        }
        row.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(sw)
        row.setOnClickListener { sw.isChecked = !sw.isChecked }
        return row
    }

    private fun weight(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dpi(8)
        }

    private fun slider(min: Int, max: Int, value: Int, onChange: (Int) -> Unit): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val label = TextView(this).apply {
            text = value.toString()
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.END
            width = dpi(52)
        }
        val bar = SeekBar(this).apply {
            this.max = max - min
            progress = value - min
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    val v = progress + min
                    label.text = v.toString()
                    onChange(v)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) = Unit
                override fun onStopTrackingTouch(sb: SeekBar?) = Unit
            })
        }
        box.addView(bar, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        box.addView(label)
        return box
    }
}
