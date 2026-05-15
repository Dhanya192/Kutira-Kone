package com.kutira.kone.ui.ideas

import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kutira.kone.R

class AiActivity : AppCompatActivity() {

    private val messages = mutableListOf<AiChatMessage>()
    private lateinit var adapter: AiChatAdapter
    private lateinit var recycler: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_chat)

        supportActionBar?.apply {
            title = "✨ AI Craft Assistant"
            setDisplayHomeAsUpEnabled(true)
        }

        recycler = findViewById(R.id.aiChatRecycler)
        val input = findViewById<EditText>(R.id.aiInputText)
        val send = findViewById<ImageButton>(R.id.aiSendBtn)

        adapter = AiChatAdapter(messages)
        recycler.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        recycler.adapter = adapter

        // Add a welcome message
        messages.add(AiChatMessage("👋 Hi! I'm your AI Craft Assistant. Ask me anything about upcycling fabric scraps — project ideas, step-by-step instructions, material tips, and more!", false))
        adapter.notifyItemInserted(0)

        val sendMessage = {
            val question = input.text.toString().trim()
            if (question.isNotEmpty()) {
                // Add user message
                messages.add(AiChatMessage(question, true))
                adapter.notifyItemInserted(messages.size - 1)
                recycler.scrollToPosition(messages.size - 1)
                input.setText("")
                hideKeyboard()

                // Add "Thinking..." placeholder
                val thinkingIndex = messages.size
                messages.add(AiChatMessage("Thinking...", false))
                adapter.notifyItemInserted(thinkingIndex)
                recycler.scrollToPosition(messages.size - 1)

                // Call Gemini
                GeminiHelper.ask(question) { response ->
                    runOnUiThread {
                        messages[thinkingIndex] = AiChatMessage(response, false)
                        adapter.notifyItemChanged(thinkingIndex)
                        recycler.scrollToPosition(messages.size - 1)
                    }
                }
            }
        }

        send.setOnClickListener { sendMessage() }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else false
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val view = currentFocus ?: android.view.View(this)
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
}
