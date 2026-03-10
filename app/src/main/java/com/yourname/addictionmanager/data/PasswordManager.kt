package com.yourname.addictionmanager.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.security.MessageDigest

class PasswordManager(context: Context) {

    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    private val sharedPreferences = EncryptedSharedPreferences.create(
        "password_prefs",
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    init {
        // Set default password to 1234 if not set
        if (!isPasswordSet()) {
            setPassword("1234")
        }
    }

    fun isPasswordSet(): Boolean {
        return sharedPreferences.contains(KEY_PASSWORD_HASH)
    }

    fun checkPassword(pin: String): Boolean {
        val storedHash = sharedPreferences.getString(KEY_PASSWORD_HASH, null) ?: return false
        val salt = sharedPreferences.getString(KEY_SALT, null) ?: return false
        val providedHash = hashPassword(pin, salt)
        return providedHash == storedHash
    }

    fun setPassword(pin: String) {
        val salt = generateSalt()
        val hash = hashPassword(pin, salt)
        sharedPreferences.edit()
            .putString(KEY_PASSWORD_HASH, hash)
            .putString(KEY_SALT, salt)
            .apply()
    }
    
    fun changePassword(oldPin: String, newPin: String): Boolean {
        if (!checkPassword(oldPin)) {
            return false
        }
        setPassword(newPin)
        return true
    }

    private fun hashPassword(password: String, salt: String): String {
        val bytes = (password + salt).toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }

    private fun generateSalt(): String {
        return (1..32)
            .map { kotlin.random.Random.nextInt(0, 256) }
            .joinToString("") { it.toString(16).padStart(2, '0') }
    }

    companion object {
        private const val KEY_PASSWORD_HASH = "password_hash"
        private const val KEY_SALT = "salt"
    }
}
