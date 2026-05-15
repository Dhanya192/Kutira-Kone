package com.kutira.kone.ui.chat

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kutira.kone.R
import com.kutira.kone.data.repository.FabricRepository
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {

    private val repo = FabricRepository()
    private lateinit var adapter: ChatAdapter

    private lateinit var rvChat: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: Button

    private lateinit var senderId: String
    private lateinit var receiverId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        rvChat = findViewById(R.id.rvChat)
        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSend)

        senderId = repo.currentUserId()

        if (senderId.isEmpty()) {
            android.widget.Toast.makeText(this, "Login required", android.widget.Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val rId = intent.getStringExtra("userId")

        if (rId.isNullOrEmpty()) {
            android.widget.Toast.makeText(this, "Chat error: user not found", android.widget.Toast.LENGTH_SHORT).show()
            finish()
            return
        }


        receiverId = rId

        adapter = ChatAdapter(mutableListOf(), senderId)
        rvChat.layoutManager = LinearLayoutManager(this)
        rvChat.adapter = adapter

        startListening()

        btnSend.setOnClickListener {
            val text = etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                sendMessage(text)
                etMessage.setText("")
            }
        }
    }

    private fun startListening() {
        repo.listenMessages(senderId, receiverId) { messages ->
            runOnUiThread {
                adapter.update(messages)

                if (messages.isNotEmpty()) {
                    rvChat.scrollToPosition(messages.size - 1)
                }
            }
        }
    }

    private fun sendMessage(text: String) {
        lifecycleScope.launch {
            repo.sendMessage(senderId, receiverId, text)
        }
    }
}