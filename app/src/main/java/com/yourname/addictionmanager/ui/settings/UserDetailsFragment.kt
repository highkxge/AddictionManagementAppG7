package com.yourname.addictionmanager.ui.settings

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import com.google.android.material.textfield.TextInputEditText
import com.yourname.addictionmanager.R

class UserDetailsFragment : Fragment() {

    private lateinit var nameEditText: TextInputEditText
    private lateinit var emailEditText: TextInputEditText
    private lateinit var phoneEditText: TextInputEditText
    private lateinit var saveButton: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_user_details, container, false)

        nameEditText = view.findViewById(R.id.name_edit_text)
        emailEditText = view.findViewById(R.id.email_edit_text)
        phoneEditText = view.findViewById(R.id.phone_edit_text)
        saveButton = view.findViewById(R.id.save_user_details_button)

        val toolbar: Toolbar = view.findViewById(R.id.toolbar)
        (activity as AppCompatActivity).setSupportActionBar(toolbar)
        (activity as AppCompatActivity).supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { requireActivity().supportFragmentManager.popBackStack() }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadUserDetails()
        saveButton.setOnClickListener { saveUserDetails() }
    }

    private fun loadUserDetails() {
        val sharedPrefs = requireActivity().getSharedPreferences("user_details", Context.MODE_PRIVATE)
        nameEditText.setText(sharedPrefs.getString("name", ""))
        emailEditText.setText(sharedPrefs.getString("email", ""))
        phoneEditText.setText(sharedPrefs.getString("phone", ""))
    }

    private fun saveUserDetails() {
        val sharedPrefs = requireActivity().getSharedPreferences("user_details", Context.MODE_PRIVATE)
        with(sharedPrefs.edit()) {
            putString("name", nameEditText.text.toString())
            putString("email", emailEditText.text.toString())
            putString("phone", phoneEditText.text.toString())
            apply()
        }
        Toast.makeText(requireContext(), "User details saved!", Toast.LENGTH_SHORT).show()
    }
}
