package com.marvesanttos.desafio1000

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.SystemClock
import java.security.MessageDigest
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
        if (prefs.getString("pin_hash", null) == null) setupPin() else loginPin()
    }


    private fun hashPin(pin: String) = MessageDigest.getInstance("SHA-256")
        .digest(("Desafio1000:" + pin).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun askPin(titleText: String, message: String, action: (String) -> Unit) {
        val field = EditText(this).apply {
            hint = "Senha de 6 dígitos"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(android.text.InputFilter.LengthFilter(6))
        }
        val box = LinearLayout(this).apply { setPadding(28, 8, 28, 0); addView(field) }
        val dialog = AlertDialog.Builder(this).setTitle(titleText).setMessage(message)
            .setView(box).setNegativeButton("Cancelar", null)
            .setPositiveButton("Continuar", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val pin = field.text.toString()
                if (!pin.matches(Regex("\\d{6}"))) field.error = "Digite 6 números"
                else { dialog.dismiss(); action(pin) }
            }
        }
        dialog.show()
    }

    private fun setupPin() {
        askPin("Criar senha", "Escolha uma senha numérica de 6 dígitos.") { first ->
            askPin("Confirmar senha", "Digite novamente a senha.") { second ->
                if (first != second) {
                    Toast.makeText(this, "As senhas não conferem.", Toast.LENGTH_LONG).show()
                    setupPin()
                } else {
                    prefs.edit().putString("pin_hash", hashPin(first)).apply()
                    loadRecords(); buildScreen()
                }
            }
        }
    }

    private fun loginPin() {
        val remaining = prefs.getLong("lock_until", 0L) - SystemClock.elapsedRealtime()
        if (remaining > 0) {
            AlertDialog.Builder(this).setTitle("Acesso bloqueado")
                .setMessage("Aguarde ${(remaining + 999) / 1000} segundos.")
                .setPositiveButton("OK") { _, _ -> finish() }.setCancelable(false).show()
            return
        }
        askPin("Desafio 1000 protegido", "Digite sua senha de 6 dígitos.") { pin ->
            if (hashPin(pin) == prefs.getString("pin_hash", "")) {
                prefs.edit().putInt("failed_attempts", 0).apply()
                loadRecords(); buildScreen()
            } else {
                val count = prefs.getInt("failed_attempts", 0) + 1
                if (count >= 2) {
                    prefs.edit().putInt("failed_attempts", 0)
                        .putLong("lock_until", SystemClock.elapsedRealtime() + 30_000L).apply()
                    AlertDialog.Builder(this).setTitle("Duas tentativas incorretas")
                        .setMessage("Acesso bloqueado por 30 segundos. Se quiser, abra a câmera; ela ficará visivelmente aberta e só tira foto se você tocar no botão.")
                        .setPositiveButton("Abrir câmera") { _, _ ->
                            val camera = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
                            if (camera.resolveActivity(packageManager) != null) startActivityForResult(camera, 701)
                            else { Toast.makeText(this, "Câmera indisponível.", Toast.LENGTH_LONG).show(); finish() }
                        }.setNegativeButton("Fechar") { _, _ -> finish() }
                        .setCancelable(false).show()
                } else {
                    prefs.edit().putInt("failed_attempts", count).apply()
                    AlertDialog.Builder(this).setTitle("Senha incorreta")
                        .setMessage("Resta uma tentativa antes do bloqueio.")
                        .setPositiveButton("Tentar novamente") { _, _ -> loginPin() }
                        .setNegativeButton("Sair") { _, _ -> finish() }.show()
                }
            }
        }
    }

    private fun changePin() {
        askPin("Alterar senha", "Digite a senha atual.") { old ->
            if (hashPin(old) != prefs.getString("pin_hash", "")) {
                Toast.makeText(this, "Senha atual incorreta.", Toast.LENGTH_LONG).show()
            } else askPin("Nova senha", "Escolha uma nova senha de 6 dígitos.") { next ->
                askPin("Confirmar nova senha", "Digite novamente.") { confirm ->
                    if (next == confirm) {
                        prefs.edit().putString("pin_hash", hashPin(next)).apply()
                        Toast.makeText(this, "Senha alterada!", Toast.LENGTH_LONG).show()
                    } else Toast.makeText(this, "As senhas não conferem.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showSettings() {
        AlertDialog.Builder(this)
            .setTitle("⚙️ Configurações")
            .setItems(arrayOf("Alterar senha", "Como usar o aplicativo")) { _, which ->
                when (which) {
                    0 -> changePin()
                    1 -> showHowToUse()
                }
            }
            .setNegativeButton("Fechar", null)
            .show()
    }

    private fun showHowToUse() {
        AlertDialog.Builder(this)
            .setTitle("Como usar o Desafio 1000")
            .setMessage(
                "1. Toque em um número para marcar aquele valor como guardado.\n\n" +
                "2. O número ficará verde e o total será atualizado.\n\n" +
                "3. Pressione e segure um número verde para consultar ou editar data, hora e observação.\n\n" +
                "4. Para desmarcar um valor, toque nele e confirme ou use a opção Desmarcar no registro.\n\n" +
                "5. Use ⚙️ para alterar sua senha ou abrir estas instruções."
            )
            .setPositiveButton("Entendi", null)
            .show()
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
            setBackgroundColor(Color.WHITE)
        }
        val title = TextView(this).apply {
            text = "DESAFIO 1000"
            textSize = 26f
            setTextColor(Color.rgb(25, 45, 75))
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 12)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        val settings = TextView(this).apply {
            text = "⚙️"
            textSize = 27f
            gravity = Gravity.CENTER
            contentDescription = "Configurações"
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setOnClickListener { showSettings() }
        }
        header.addView(settings, LinearLayout.LayoutParams(dp(52), dp(52)))
        root.addView(header)
        summary = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(8, 8, 8, 16)
        }
        root.addView(summary)
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
                setTextColor(if (saved) Color.WHITE else Color.rgb(55, 65, 80))
                setPadding(2, 2, 2, 2)
                includeFontPadding = true
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(9).toFloat()
                    setColor(if (saved) green else Color.rgb(242, 244, 247))
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
