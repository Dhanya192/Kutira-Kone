package com.kutira.kone

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.kutira.kone.data.repository.FabricRepository
import com.kutira.kone.databinding.ActivityAuthBinding
import kotlinx.coroutines.launch

class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding
    private val repo = FabricRepository()
    private var isLogin = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        updateUI()

        binding.btnToggle.setOnClickListener {
            isLogin = !isLogin
            updateUI()
        }

        binding.btnAction.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password.length < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.progressBar.visibility = View.VISIBLE
            binding.btnAction.isEnabled = false

            if (isLogin) {
                lifecycleScope.launch {
                    val result = repo.signIn(email, password)
                    binding.progressBar.visibility = View.GONE
                    binding.btnAction.isEnabled = true
                    result.fold(
                        onSuccess = { goToMain() },
                        onFailure = { Toast.makeText(this@AuthActivity, it.message, Toast.LENGTH_LONG).show() }
                    )
                }
            } else {
                val name = binding.etName.text.toString().trim()
                if (name.isEmpty()) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnAction.isEnabled = true
                    Toast.makeText(this, "Please enter your name", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                lifecycleScope.launch {
                    val result = repo.signUp(name, email, password)
                    binding.progressBar.visibility = View.GONE
                    binding.btnAction.isEnabled = true
                    result.fold(
                        onSuccess = { goToMain() },
                        onFailure = { Toast.makeText(this@AuthActivity, it.message, Toast.LENGTH_LONG).show() }
                    )
                }
            }
        }

        // Demo mode - skip auth
        binding.btnDemo.setOnClickListener {
            goToMain()
        }
    }

    private fun updateUI() {
        if (isLogin) {
            binding.tvTitle.text = "Welcome Back"
            binding.tvSubtitle.text = "Sign in to trade fabric scraps"
            binding.etName.visibility = View.GONE
            binding.btnAction.text = "Sign In"
            binding.btnToggle.text = "New here? Create Account"
        } else {
            binding.tvTitle.text = "Join Kutira-Kone"
            binding.tvSubtitle.text = "Start trading fabric scraps today"
            binding.etName.visibility = View.VISIBLE
            binding.btnAction.text = "Create Account"
            binding.btnToggle.text = "Already have an account? Sign In"
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
