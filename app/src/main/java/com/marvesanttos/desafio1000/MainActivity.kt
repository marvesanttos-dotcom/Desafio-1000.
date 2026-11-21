package com.marvesanttos.desafio1000

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("desafio1000", MODE_PRIVATE) }
    private val records = mutableMapOf<Int, JSONObject>()
    private lateinit var grid: GridLayout
    private lateinit var summary: TextView
    private val green = Color.rgb(35, 145, 75)
    private val blue = Color.rgb(35, 95, 170)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadRecords()
        buildScreen()
    }

    private fun loadRecords() {
        for (n in 1..1000) {
            val raw = prefs.getString("n$n", null) ?: continue
            try { records[n] = JSONObject(raw) } catch (_: Exception) {}
        }
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 20, 18, 12)
            setBackgroundColor(Color.rgb(247, 249, 252))
        }
        val title = TextView(this).apply {
            text = "DESAFIO 1000"
            textSize = 26f
            setTextColor(Color.rgb(25, 45, 75))
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 12)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        root.addView(title)
        summary = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(8, 8, 8, 16)
        }
        root.addView(summary)
        val hint = TextView(this).apply {
            text = "Toque em um número para marcar como guardado. Segure para ver ou editar data, hora e observação."
            textSize = 13f
            setTextColor(Color.DKGRAY)
            setPadding(4, 0, 4, 12)
        }
        root.addView(hint)
        val scroll = ScrollView(this)
        grid = GridLayout(this).apply { columnCount = 5; useDefaultMargins = true }
        scroll.addView(grid)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        refreshGrid()
    }

    private fun refreshGrid() {
        grid.removeAllViews()
        for (n in 1..1000) {
            val saved = records.containsKey(n)
            val cell = TextView(this).apply {
                text = n.toString()
                textSize = 28f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setPadding(2, 2, 2, 2)
                includeFontPadding = true
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(9).toFloat()
                    setColor(if (saved) green else blue)
                }
                setOnClickListener { toggle(n) }
                setOnLongClickListener { editRecord(n); true }
            }
            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = dp(44)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(2, 2, 2, 2)
            }
            grid.addView(cell, params)
        }
        updateSummary()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun updateSummary() {
        val total = records.keys.sum()
        summary.text = "Guardados: ${records.size}/1000   •   Total: R$ ${String.format(Locale("pt", "BR"), "%,.2f", total.toDouble())}"
    }

    private fun toggle(n: Int) {
        if (records.containsKey(n)) {
            AlertDialog.Builder(this).setTitle("Número $n")
                .setMessage("Deseja desmarcar este valor como guardado?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Desmarcar") { _, _ ->
                    records.remove(n); prefs.edit().remove("n$n").apply(); refreshGrid()
                }.show()
        } else {
            val now = Date()
            val date = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(now)
            val time = SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(now)
            val item = JSONObject().put("date", date).put("time", time).put("note", "")
            records[n] = item
            prefs.edit().putString("n$n", item.toString()).apply()
            refreshGrid()
            Toast.makeText(this, "R$ $n marcado como guardado!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun editRecord(n: Int) {
        val existing = records[n]
        if (existing == null) {
            AlertDialog.Builder(this).setTitle("Número $n")
                .setMessage("Este número ainda não foi marcado como guardado. Toque nele primeiro.")
                .setPositiveButton("OK", null).show()
            return
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 8, 24, 0)
        }
        val date = EditText(this).apply { hint = "Data (dd/MM/aaaa)"; setText(existing.optString("date")) }
        val time = EditText(this).apply { hint = "Hora (HH:mm)"; setText(existing.optString("time")) }
        val note = EditText(this).apply { hint = "Observação"; setText(existing.optString("note")); minLines = 2 }
        box.addView(date); box.addView(time); box.addView(note)
        date.setOnClickListener {
            val cal = java.util.Calendar.getInstance()
            DatePickerDialog(this, { _, y, m, d -> date.setText(String.format(Locale("pt", "BR"), "%02d/%02d/%04d", d, m + 1, y)) },
                cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH)).show()
        }
        time.setOnClickListener {
            val cal = java.util.Calendar.getInstance()
            TimePickerDialog(this, { _, h, min -> time.setText(String.format(Locale("pt", "BR"), "%02d:%02d", h, min)) },
                cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE), true).show()
        }
        AlertDialog.Builder(this).setTitle("Registro do número $n")
            .setView(box)
            .setNegativeButton("Fechar", null)
            .setNeutralButton("Desmarcar") { _, _ ->
                records.remove(n); prefs.edit().remove("n$n").apply(); refreshGrid()
            }
            .setPositiveButton("Salvar") { _, _ ->
                existing.put("date", date.text.toString())
                    .put("time", time.text.toString()).put("note", note.text.toString())
                prefs.edit().putString("n$n", existing.toString()).apply()
                Toast.makeText(this, "Registro salvo", Toast.LENGTH_SHORT).show()
            }.show()
    }
}
