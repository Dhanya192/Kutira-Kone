package com.kutira.kone.ui.ideas

import com.kutira.kone.BuildConfig
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

object GeminiHelper {

    private val client = OkHttpClient()

    // Gemini API URL
    private val BASE_URL: String
        get() = "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent?key=${BuildConfig.GEMINI_API_KEY}"

    // System prompt
    private val SYSTEM_PROMPT =
        "You are a craft assistant for a fabric upcycling app in India. " +
                "Help users make handmade items from fabric scraps. " +
                "Be brief and practical."

    fun ask(prompt: String, callback: (String) -> Unit) {

        // Request body
        val part = JSONObject().put(
            "text",
            "$SYSTEM_PROMPT\n\nUser: $prompt"
        )

        val partsArray = JSONArray().put(part)

        val contentObj = JSONObject().put(
            "parts",
            partsArray
        )

        val contentsArray = JSONArray().put(contentObj)

        // Increase token limit for full response
        val generationConfig = JSONObject().apply {
            put("maxOutputTokens", 1000)
            put("temperature", 0.7)
        }

        val json = JSONObject().apply {
            put("contents", contentsArray)
            put("generationConfig", generationConfig)
        }

        val body = RequestBody.create(
            "application/json".toMediaTypeOrNull(),
            json.toString()
        )

        val request = Request.Builder()
            .url(BASE_URL)
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                callback(
                    "Network error: ${e.message}\n\n" +
                            "Check your internet connection and try again."
                )
            }

            override fun onResponse(call: Call, response: Response) {
                try {

                    val res = response.body?.string() ?: ""

                    if (!response.isSuccessful) {
                        callback(
                            "Server error: ${response.code}\n$res"
                        )
                        return
                    }

                    val jsonObj = JSONObject(res)

                    // Handle API errors
                    if (jsonObj.has("error")) {

                        val errorObj =
                            jsonObj.getJSONObject("error")

                        val code =
                            errorObj.optInt("code", 0)

                        val msg =
                            errorObj.optString(
                                "message",
                                "Unknown error"
                            )

                        val friendlyMsg = when (code) {

                            429 ->
                                "⚠️ Too many requests.\n\n" +
                                        "Please wait 1 minute and try again.\n" +
                                        "Tip: Free tier allows limited requests."

                            403 ->
                                "⚠️ API key invalid or quota exhausted.\n\n" +
                                        "Get a new API key from AI Studio."

                            else ->
                                "⚠️ Error ($code): $msg"
                        }

                        callback(friendlyMsg)
                        return
                    }

                    // Read FULL Gemini response
                    val candidates =
                        jsonObj.optJSONArray("candidates")

                    if (candidates == null ||
                        candidates.length() == 0
                    ) {
                        callback("No response from Gemini.")
                        return
                    }

                    val content = candidates
                        .getJSONObject(0)
                        .getJSONObject("content")

                    val parts =
                        content.getJSONArray("parts")

                    val fullText = StringBuilder()

                    // Combine all response parts
                    for (i in 0 until parts.length()) {

                        val text = parts
                            .getJSONObject(i)
                            .optString("text", "")

                        fullText.append(text)
                    }

                    val finalResponse =
                        fullText.toString().trim()

                    callback(
                        if (finalResponse.isNotEmpty())
                            finalResponse
                        else
                            "No text response generated."
                    )

                } catch (e: Exception) {
                    callback(
                        "Parsing error: ${e.message}"
                    )
                }
            }
        })
    }
}