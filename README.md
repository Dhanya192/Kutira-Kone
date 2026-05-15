# Kutira-Kone 🧵

A fabric scrap upcycling Android app for villages across India. Browse, list, and swap fabric scraps. Includes an AI craft assistant powered by Google Gemini.

## Setup

### 1. Clone the repo
```bash
git clone <your-repo-url>
cd KutiraKone_fixed
```

### 2. Add your API key in `local.properties`
Create or open `local.properties` in the project root (this file is gitignored — never commit it):

```properties
sdk.dir=/path/to/your/android/sdk
GEMINI_API_KEY=your_gemini_api_key_here
```

> Get a free Gemini API key at https://aistudio.google.com/app/apikey

### 3. Add Firebase config
Place your `google-services.json` in `app/` (also gitignored).

### 4. Build & Run
Open in Android Studio and run on a device or emulator (min SDK 24).

---

## AI Chat — What was fixed

| Issue | Old code | Fixed |
|---|---|---|
| Wrong model name | `gemini-2.0-flash-lite` (doesn't exist) | `gemini-2.0-flash-latest` ✅ |
| API key in source code | Hardcoded string in `.kt` file | `local.properties` → `BuildConfig` ✅ |
| HTTP library | `HttpURLConnection` (verbose) | `OkHttp` (already in deps) ✅ |
| API key delivery | `?key=` query param | `X-goog-api-key` header (matches curl) ✅ |
| GitHub safety | API key would be committed | `local.properties` is gitignored ✅ |

## Project Structure

```
app/src/main/java/com/kutira/kone/
├── ui/
│   ├── ideas/      ← AI craft assistant lives here (IdeasFragment.kt)
│   ├── chat/       ← Peer-to-peer chat
│   ├── home/       ← Fabric scrap listings
│   ├── browse/     ← Map view
│   ├── swap/       ← Swap requests
│   └── upload/     ← Upload a scrap
├── data/
│   ├── model/      ← Data classes
│   └── repository/ ← Firebase + GCS logic
└── MainActivity.kt
```
