package com.example.calculadoracientifica

import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridLayout
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
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
    private lateinit var txtResultado: TextView
    private lateinit var txtHistorial: TextView
    private lateinit var txtContador: TextView
    private lateinit var panelCalculadora: View
    private lateinit var panelHistorial: View
    private lateinit var prefs: SharedPreferences

    private var expresion = ""                        // lo que se ve en la pantalla
    private var recienCalculado = false               // true justo después de pulsar =
    private val historial = mutableListOf<String>()   // la operación más reciente va primero
    private var modoOscuro = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // El modo oscuro se elige antes de crear la pantalla
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
        txtResultado = findViewById(R.id.txtResultado)
        txtHistorial = findViewById(R.id.txtHistorial)
        txtContador = findViewById(R.id.txtContador)
        panelCalculadora = findViewById(R.id.panelCalculadora)
        panelHistorial = findViewById(R.id.panelHistorial)

        // Cada botón del teclado envía su texto a pulsar()
        val teclado = findViewById<GridLayout>(R.id.teclado)
        for (i in 0 until teclado.childCount) {
            val boton = teclado.getChildAt(i) as Button
            if (boton is MaterialButton) {
                boton.rippleColor = ColorStateList.valueOf(Color.argb(60, 128, 128, 128))  // efecto al tocar
            }
            boton.setOnClickListener { pulsar(boton.text.toString()) }
        }

        findViewById<View>(R.id.btnHistorial).setOnClickListener { mostrarHistorial(true) }
        findViewById<View>(R.id.btnVolver).setOnClickListener { mostrarHistorial(false) }
        findViewById<View>(R.id.btnAjustes).setOnClickListener { abrirMenu(it) }

        // El botón Atrás del teléfono cierra el historial
        onBackPressedDispatcher.addCallback(this) {
            if (panelHistorial.isVisible) mostrarHistorial(false) else finish()
        }

        // Recuperar el historial guardado y la expresión (al cambiar de modo la pantalla se recrea)
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

    // ---------------- Teclado ----------------

    private fun pulsar(tecla: String) {
        if (tecla == "=") {
            calcular()
            return
        }
        // Después de un resultado, un número nuevo empieza otra operación
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
            txtExpresion.text = expresion
            txtResultado.text = ""
        } catch (e: Exception) {
            txtResultado.text = getString(R.string.error)
        }
    }

    // Muestra la expresión y, si ya hay una operación, su resultado previo
    private fun actualizarPantalla() {
        txtExpresion.text = expresion.ifEmpty { "0" }
        txtResultado.text = ""
        if (expresion.any { it in "+−×÷(" }) {
            try {
                val previo = formatear(evaluar(cerrarParentesis(expresion)))
                txtResultado.text = getString(R.string.resultado_previo, previo)
            } catch (e: Exception) {
                // La expresión aún está incompleta: no se muestra nada
            }
        }
    }

    // Agrega los ")" que falten al final
    private fun cerrarParentesis(expr: String): String {
        val faltan = expr.count { it == '(' } - expr.count { it == ')' }
        return expr + ")".repeat(maxOf(faltan, 0))
    }

    // 20.0 -> "20", 0.30000000000000004 -> "0.3", -5.0 -> "−5"
    private fun formatear(valor: Double): String {
        if (abs(valor) < 1e-10) return "0"
        return String.format(Locale.US, "%.10f", valor).trimEnd('0').trimEnd('.').replace('-', '−')
    }

    // ---------------- Cálculo ----------------
    // La expresión se lee de izquierda a derecha respetando la jerarquía:
    // primero paréntesis y funciones, después × ÷ y al final + −

    private var texto = ""
    private var pos = 0

    private fun evaluar(expr: String): Double {
        texto = expr
        pos = 0
        val resultado = sumaResta()
        // Sobró texto sin leer, o hubo división entre 0
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

    // Un número, un paréntesis, una función o un número negativo
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
                val radianes = Math.toRadians(factor())   // factor() lee el paréntesis: (30)
                if (funcion == "tan" && abs(cos(radianes)) < 1e-10) throw ArithmeticException()  // tan(90)
                return when (funcion) {
                    "sen" -> sin(radianes)
                    "cos" -> cos(radianes)
                    else -> tan(radianes)
                }
            }
        }

        val inicio = pos
        while (pos < texto.length && (texto[pos].isDigit() || texto[pos] == '.')) pos++
        return texto.substring(inicio, pos).toDouble()   // lanza error si no hay un número válido
    }

    // ---------------- Historial ----------------

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

    // ---------------- Menú del engranaje ----------------

    private fun abrirMenu(boton: View) {
        val menu = PopupMenu(this, boton)
        menu.menu.add(0, 1, 0, R.string.modo_oscuro).apply {
            isCheckable = true
            isChecked = modoOscuro
        }
        menu.menu.add(0, 2, 1, R.string.borrar_historial)

        menu.setOnMenuItemClickListener { item ->
            if (item.itemId == 1) {
                // Guarda la preferencia; Android vuelve a crear la pantalla en el otro modo
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

    // ---------------- Modo oscuro ----------------
    // activity_main.xml usa los colores claros. En modo oscuro se recorre toda la
    // pantalla y cada color claro se cambia por su pareja oscura de esta tabla.

    private val coloresOscuros = mapOf(
        "#E6E8EA" to "#1A1C1F",   // fondo
        "#F3F4F5" to "#222529",   // pantalla, historial y botones de arriba
        "#FAFAFB" to "#2B2F34",   // botones de números
        "#D7DBDF" to "#363B41",   // botones de operaciones
        "#CAD4DC" to "#2D3944",   // botones SEN, COS, TAN
        "#E4D4D0" to "#43302C",   // botón C
        "#56687A" to "#8193A5",   // botón =
        "#262A2E" to "#E4E6E9",   // texto principal
        "#5C636A" to "#9BA3AB",   // texto secundario
        "#3E454C" to "#C9CED3",   // íconos de arriba
        "#22313D" to "#C7D5E1",   // texto de SEN, COS, TAN
        "#6A372E" to "#E9C3BA",   // texto de C
        "#FFFFFF" to "#101418"    // texto de =
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
        if (vista is TextView) {
            coloresOscuros[vista.currentTextColor]?.let { vista.setTextColor(it) }
        }
        if (vista is ViewGroup) {
            for (i in 0 until vista.childCount) pintarOscuro(vista.getChildAt(i))
        }
    }
}
