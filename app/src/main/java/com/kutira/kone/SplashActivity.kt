package com.kutira.kone

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import com.kutira.kone.data.repository.FabricRepository
import com.kutira.kone.databinding.ActivitySplashBinding

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val repo = FabricRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val fadeIn = AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
        binding.logoText.startAnimation(fadeIn)
        binding.tagline.startAnimation(fadeIn)

        Handler(Looper.getMainLooper()).postDelayed({
            val intent = if (repo.isLoggedIn()) {
                Intent(this, MainActivity::class.java)
            } else {
                Intent(this, AuthActivity::class.java)
            }
            startActivity(intent)
            finish()
        }, 2000)
    }
}
