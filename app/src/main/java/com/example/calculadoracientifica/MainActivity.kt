package com.example.calculadoracientifica

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

class MainActivity : AppCompatActivity() {

    private lateinit var txtExpresion: TextView
    private lateinit var txtHistorial: TextView
    private lateinit var txtContador: TextView
    private lateinit var panelCalculadora: View
    private lateinit var panelHistorial: View
    private lateinit var prefs: SharedPreferences

    private var expresion = ""
    private var recienCalculado = false
    private val historial = mutableListOf<String>()
    private var modoOscuro = false

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = getSharedPreferences("calculadora", MODE_PRIVATE)
        modoOscuro = prefs.getBoolean("modoOscuro", false)
        AppCompatDelegate.setDefaultNightMode(
            if (modoOscuro) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        txtExpresion = findViewById(R.id.txtExpresion)
        txtHistorial = findViewById(R.id.txtHistorial)
        txtContador = findViewById(R.id.txtContador)
        panelCalculadora = findViewById(R.id.panelCalculadora)
        panelHistorial = findViewById(R.id.panelHistorial)

        val teclado = findViewById<GridLayout>(R.id.teclado)
        for (i in 0 until teclado.childCount) {
            val boton = teclado.getChildAt(i) as Button
            if (boton is MaterialButton) {
                boton.rippleColor = ColorStateList.valueOf(Color.argb(60, 128, 128, 128))
            }
            boton.setOnClickListener { pulsar(boton.text.toString()) }
        }

        findViewById<View>(R.id.btnHistorial).setOnClickListener { mostrarHistorial(true) }
        findViewById<View>(R.id.btnVolver).setOnClickListener { mostrarHistorial(false) }
        findViewById<View>(R.id.btnAjustes).setOnClickListener { abrirMenu(it) }

        onBackPressedDispatcher.addCallback(this) {
            if (panelHistorial.isVisible) mostrarHistorial(false) else finish()
        }

        val guardado = prefs.getString("historial", "") ?: ""
        historial.addAll(guardado.split("|").filter { it.isNotEmpty() })
        expresion = savedInstanceState?.getString("expresion") ?: ""

        actualizarPantalla()
        actualizarHistorial()
        if (modoOscuro) pintarOscuro(findViewById(R.id.main))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("expresion", expresion)
    }

    private fun pulsar(tecla: String) {
        if (tecla == "=") {
            if (expresion == "666") abrirDoom() else calcular()
            return
        }
        if (recienCalculado && tecla !in listOf("+", "−", "×", "÷", "⌫")) expresion = ""
        recienCalculado = false

        expresion = when (tecla) {
            "C" -> ""
            "⌫" -> if (listOf("sen(", "cos(", "tan(").any { expresion.endsWith(it) }) {
                expresion.dropLast(4)
            } else {
                expresion.dropLast(1)
            }
            "SEN" -> expresion + "sen("
            "COS" -> expresion + "cos("
            "TAN" -> expresion + "tan("
            else -> expresion + tecla
        }
        actualizarPantalla()
    }

    private fun calcular() {
        if (expresion.isEmpty() || recienCalculado) return
        try {
            val completa = cerrarParentesis(expresion)
            val resultado = formatear(evaluar(completa))
            guardarEnHistorial("$completa\n= $resultado")
            expresion = resultado
            recienCalculado = true
            actualizarPantalla()
        } catch (e: Exception) {
            expresion = ""
            txtExpresion.text = getString(R.string.error)
        }
    }

    private fun abrirDoom() {
        expresion = ""
        actualizarPantalla()
        try {
            startActivity(Intent(Intent.ACTION_VIEW, getString(R.string.url_doom).toUri()))
        } catch (e: ActivityNotFoundException) {
            txtExpresion.text = getString(R.string.error)
        }
    }

    private fun actualizarPantalla() {
        txtExpresion.text = expresion.ifEmpty { "0" }
    }

    private fun cerrarParentesis(expr: String): String {
        val faltan = expr.count { it == '(' } - expr.count { it == ')' }
        return expr + ")".repeat(maxOf(faltan, 0))
    }

    private fun formatear(valor: Double): String {
        if (abs(valor) < 1e-10) return "0"
        return String.format(Locale.US, "%.10f", valor).trimEnd('0').trimEnd('.').replace('-', '−')
    }

    private var texto = ""
    private var pos = 0

    private fun evaluar(expr: String): Double {
        texto = expr
        pos = 0
        val resultado = sumaResta()
        if (pos < texto.length || resultado.isNaN() || resultado.isInfinite()) throw ArithmeticException()
        return resultado
    }

    private fun sumaResta(): Double {
        var resultado = multiplicacionDivision()
        while (pos < texto.length && texto[pos] in "+−") {
            val operador = texto[pos++]
            val siguiente = multiplicacionDivision()
            resultado = if (operador == '+') resultado + siguiente else resultado - siguiente
        }
        return resultado
    }

    private fun multiplicacionDivision(): Double {
        var resultado = factor()
        while (pos < texto.length && texto[pos] in "×÷") {
            val operador = texto[pos++]
            val siguiente = factor()
            resultado = if (operador == '×') resultado * siguiente else resultado / siguiente
        }
        return resultado
    }

    private fun factor(): Double {
        if (pos >= texto.length) throw ArithmeticException()

        if (texto[pos] == '−') {
            pos++
            return -factor()
        }

        if (texto[pos] == '(') {
            pos++
            val resultado = sumaResta()
            if (pos >= texto.length || texto[pos] != ')') throw ArithmeticException()
            pos++
            return resultado
        }

        for (funcion in listOf("sen", "cos", "tan")) {
            if (texto.startsWith(funcion, pos)) {
                pos += 3
                val radianes = Math.toRadians(factor())
                if (funcion == "tan" && abs(cos(radianes)) < 1e-10) throw ArithmeticException()
                return when (funcion) {
                    "sen" -> sin(radianes)
                    "cos" -> cos(radianes)
                    else -> tan(radianes)
                }
            }
        }

        val inicio = pos
        while (pos < texto.length && (texto[pos].isDigit() || texto[pos] == '.')) pos++
        return texto.substring(inicio, pos).toDouble()
    }

    private fun guardarEnHistorial(operacion: String) {
        historial.add(0, operacion)
        if (historial.size > 10) historial.removeAt(10)
        prefs.edit { putString("historial", historial.joinToString("|")) }
        actualizarHistorial()
    }

    private fun actualizarHistorial() {
        txtHistorial.text = if (historial.isEmpty()) {
            getString(R.string.historial_vacio)
        } else {
            historial.joinToString("\n\n")
        }
        txtContador.text = getString(R.string.contador_historial, historial.size)
    }

    private fun mostrarHistorial(mostrar: Boolean) {
        panelHistorial.isVisible = mostrar
        panelCalculadora.isVisible = !mostrar
    }

    private fun abrirMenu(boton: View) {
        val tema = if (modoOscuro) android.R.style.Theme_Material else android.R.style.Theme_Material_Light
        val menu = PopupMenu(ContextThemeWrapper(this, tema), boton)
        menu.menu.add(0, 1, 0, if (modoOscuro) R.string.modo_claro else R.string.modo_oscuro)
        menu.menu.add(0, 2, 1, R.string.borrar_historial)

        menu.setOnMenuItemClickListener { item ->
            if (item.itemId == 1) {
                prefs.edit { putBoolean("modoOscuro", !modoOscuro) }
                AppCompatDelegate.setDefaultNightMode(
                    if (!modoOscuro) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
                )
            } else {
                historial.clear()
                prefs.edit { remove("historial") }
                actualizarHistorial()
            }
            true
        }
        menu.show()
    }

    private val coloresOscuros = mapOf(
        "#808080" to "#1A1A1A",
        "#FFFFFF" to "#333333",
        "#000000" to "#2B2F34",
        "#FF0000" to "#660000",
        "#00FF00" to "#274E13"
    ).entries.associate { it.key.toColorInt() to it.value.toColorInt() }

    private fun pintarOscuro(vista: View) {
        val fondo = vista.background
        if (fondo is ColorDrawable) {
            coloresOscuros[fondo.color]?.let { vista.setBackgroundColor(it) }
        }
        if (vista is MaterialButton && vista.backgroundTintList != null) {
            coloresOscuros[vista.backgroundTintList!!.defaultColor]?.let {
                vista.backgroundTintList = ColorStateList.valueOf(it)
            }
        }
        if (vista is TextView) vista.setTextColor(Color.WHITE)
        if (vista is ViewGroup) {
            for (i in 0 until vista.childCount) pintarOscuro(vista.getChildAt(i))
        }
    }
}
