package com.yourname.addictionmanager.ui.password

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.yourname.addictionmanager.R
import com.yourname.addictionmanager.data.PasswordManager

enum class PasswordAction {
    CREATE,
    CONFIRM,
    ENTER,
    CHANGE
}

class PasswordActivity : AppCompatActivity() {

    private lateinit var passwordManager: PasswordManager
    private var action: PasswordAction? = null
    private var firstPin: String? = null
    private var oldPin: String? = null

    private lateinit var pinDigits: List<EditText>
    private var isHandlingPin = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_password)

        passwordManager = PasswordManager(this)
        action = intent.getSerializableExtra(EXTRA_ACTION) as? PasswordAction
        firstPin = intent.getStringExtra(EXTRA_FIRST_PIN)
        oldPin = intent.getStringExtra(EXTRA_OLD_PIN)

        val prompt: TextView = findViewById(R.id.password_prompt)
        pinDigits = listOf(
            findViewById(R.id.pin_digit_1),
            findViewById(R.id.pin_digit_2),
            findViewById(R.id.pin_digit_3),
            findViewById(R.id.pin_digit_4)
        )

        prompt.text = when (action) {
            PasswordAction.CREATE -> "Create a new PIN"
            PasswordAction.CONFIRM -> "Confirm your new PIN"
            PasswordAction.ENTER -> "Enter your PIN to continue"
            PasswordAction.CHANGE -> "Enter your old PIN"
            else -> ""
        }

        setupPinListeners()
    }

    private fun setupPinListeners() {
        for (i in pinDigits.indices) {
            pinDigits[i].addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (isHandlingPin) return
                    
                    if (s?.length == 1) {
                        if (i < pinDigits.size - 1) {
                            pinDigits[i + 1].requestFocus()
                        }
                    }

                    val pin = pinDigits.joinToString("") { it.text.toString() }
                    if (pin.length == 4) {
                        isHandlingPin = true
                        handlePin(pin)
                    }
                }
                override fun afterTextChanged(s: Editable?) {}
            })

            // Handle backspace
            pinDigits[i].setOnKeyListener { v, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_DEL && event.action == KeyEvent.ACTION_DOWN) {
                    if (pinDigits[i].text.isEmpty() && i > 0) {
                        pinDigits[i - 1].requestFocus()
                        pinDigits[i - 1].text.clear()
                        return@setOnKeyListener true
                    }
                }
                false
            }
        }
    }

    private fun handlePin(pin: String) {
        when (action) {
            PasswordAction.CREATE -> {
                val intent = Intent(this, PasswordActivity::class.java).apply {
                    putExtra(EXTRA_ACTION, PasswordAction.CONFIRM)
                    putExtra(EXTRA_FIRST_PIN, pin)
                    putExtra(EXTRA_OLD_PIN, oldPin)
                }
                startActivity(intent)
                finish()
            }
            PasswordAction.CONFIRM -> {
                if (pin == firstPin) {
                    showConfirmationDialog(pin)
                } else {
                    Toast.makeText(this, "PINs do not match", Toast.LENGTH_SHORT).show()
                    resetPin()
                }
            }
            PasswordAction.ENTER -> {
                if (passwordManager.checkPassword(pin)) {
                    setResult(Activity.RESULT_OK)
                    finish()
                } else {
                    Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show()
                    resetPin()
                }
            }
            PasswordAction.CHANGE -> {
                if (passwordManager.checkPassword(pin)) {
                    val intent = Intent(this, PasswordActivity::class.java).apply {
                        putExtra(EXTRA_ACTION, PasswordAction.CREATE)
                        putExtra(EXTRA_OLD_PIN, pin)
                    }
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(this, "Incorrect old PIN", Toast.LENGTH_SHORT).show()
                    resetPin()
                }
            }
            else -> {}
        }
    }

    private fun resetPin() {
        isHandlingPin = false
        pinDigits.forEach { it.text.clear() }
        pinDigits[0].requestFocus()
    }

    private fun showConfirmationDialog(pin: String) {
        AlertDialog.Builder(this)
            .setTitle("Confirm Password")
            .setMessage("Are you sure you want to set this as your new password?")
            .setPositiveButton("Confirm") { _, _ ->
                if (oldPin != null) {
                    passwordManager.changePassword(oldPin!!, pin)
                    Toast.makeText(this, "Password changed!", Toast.LENGTH_SHORT).show()
                } else {
                    passwordManager.setPassword(pin)
                    Toast.makeText(this, "Password set!", Toast.LENGTH_SHORT).show()
                }
                setResult(Activity.RESULT_OK)
                finish()
            }
            .setNegativeButton("Cancel") { _, _ ->
                resetPin()
            }
            .setCancelable(false)
            .show()
    }

    companion object {
        const val EXTRA_ACTION = "EXTRA_ACTION"
        const val EXTRA_FIRST_PIN = "EXTRA_FIRST_PIN"
        const val EXTRA_OLD_PIN = "EXTRA_OLD_PIN"
    }
}
