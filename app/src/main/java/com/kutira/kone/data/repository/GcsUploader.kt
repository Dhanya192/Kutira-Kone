package com.kutira.kone.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.UUID

object GcsUploader {

    private val client = OkHttpClient()
    private const val BUCKET = "kutirakone-images" // ← change this

    suspend fun uploadImage(context: Context, uri: Uri): String? =
        withContext(Dispatchers.IO) {
            try {
                // 1. Compress image
                val inputStream = context.contentResolver.openInputStream(uri)
                val original = BitmapFactory.decodeStream(inputStream)
                val maxWidth = 800
                val ratio = maxWidth.toFloat() / original.width
                val scaled = Bitmap.createScaledBitmap(
                    original, maxWidth, (original.height * ratio).toInt(), true
                )
                val baos = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 75, baos)
                val imageBytes = baos.toByteArray()

                // 2. Get access token
                val token = getAccessToken(context) ?: return@withContext null

                // 3. Upload to GCS
                val fileName = "scraps/${UUID.randomUUID()}.jpg"
                val uploadUrl = "https://storage.googleapis.com/upload/storage/v1/b/$BUCKET/o" +
                        "?uploadType=media&name=$fileName"

                val requestBody = imageBytes.toRequestBody("image/jpeg".toMediaType())
                val request = Request.Builder()
                    .url(uploadUrl)
                    .addHeader("Authorization", "Bearer $token")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    android.util.Log.e("GCS", "Upload failed: ${response.body?.string()}")
                    return@withContext null
                }

                // 4. Make file public
                makeFilePublic(fileName, token)

                // 5. Return public URL
                "https://storage.googleapis.com/$BUCKET/$fileName"

            } catch (e: Exception) {
                android.util.Log.e("GCS", "Exception: ${e.message}")
                null
            }
        }

    private fun getAccessToken(context: Context): String? {
        return try {
            val json = context.assets.open("service_account.json")
                .bufferedReader().readText()
            val sa = JSONObject(json)

            val privateKeyPem = sa.getString("private_key")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("\n", "")
                .trim()

            val clientEmail = sa.getString("client_email")

            // Build JWT manually (no jjwt library needed)
            val now = System.currentTimeMillis() / 1000
            val header = Base64.encodeToString(
                """{"alg":"RS256","typ":"JWT"}""".toByteArray(),
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )
            val payload = Base64.encodeToString(
                """
                {
                    "iss":"$clientEmail",
                    "sub":"$clientEmail",
                    "aud":"https://oauth2.googleapis.com/token",
                    "scope":"https://www.googleapis.com/auth/devstorage.read_write",
                    "iat":$now,
                    "exp":${now + 3600}
                }
                """.trimIndent().toByteArray(),
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )

            val signingInput = "$header.$payload"

            // Sign with RSA private key
            val keyBytes = Base64.decode(privateKeyPem, Base64.DEFAULT)
            val keySpec = PKCS8EncodedKeySpec(keyBytes)
            val privateKey = KeyFactory.getInstance("RSA").generatePrivate(keySpec)
            val signature = Signature.getInstance("SHA256withRSA").apply {
                initSign(privateKey)
                update(signingInput.toByteArray())
            }.sign()

            val sig = Base64.encodeToString(
                signature,
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )

            val jwt = "$signingInput.$sig"

            // Exchange JWT for access token
            val body = "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=$jwt"
            val tokenRequest = Request.Builder()
                .url("https://oauth2.googleapis.com/token")
                .post(body.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
                .build()

            val tokenResponse = client.newCall(tokenRequest).execute()
            val tokenJson = JSONObject(tokenResponse.body?.string() ?: return null)
            tokenJson.getString("access_token")

        } catch (e: Exception) {
            android.util.Log.e("GCS", "Token error: ${e.message}")
            null
        }
    }

    private fun makeFilePublic(fileName: String, token: String) {
        try {
            val encodedName = fileName.replace("/", "%2F")
            val body = """{"role":"READER","entity":"allUsers"}"""
                .toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("https://storage.googleapis.com/storage/v1/b/$BUCKET/o/$encodedName/acl")
                .addHeader("Authorization", "Bearer $token")
                .post(body)
                .build()
            client.newCall(request).execute()
        } catch (e: Exception) {
            android.util.Log.e("GCS", "Make public error: ${e.message}")
        }
    }
}