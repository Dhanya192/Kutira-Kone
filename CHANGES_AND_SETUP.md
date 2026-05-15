# KutiraKone v2 — Changes & Setup Guide

## What's Changed

### 1. 🤖 AI switched from Claude → Gemini (FREE)
- File: `app/src/main/java/.../ui/ideas/IdeasFragment.kt`
- Uses **Gemini 2.0 Flash** — free tier, no credit card needed
- Same craft assistant functionality, multi-turn conversation supported

**How to get your free Gemini API key:**
1. Go to https://aistudio.google.com/app/apikey
2. Sign in with any Google account
3. Click "Create API Key" → copy it
4. Open `IdeasFragment.kt` and replace `YOUR_GEMINI_API_KEY_HERE` with your key:
   ```kotlin
   private val GEMINI_API_KEY = "AIza...your-key-here..."
   ```
5. Free quota: 15 requests/minute, 1,500/day — more than enough!

---

### 2. 📷 "Add Photo" prompt on scrap list cards
- File: `app/src/main/res/layout/item_scrap.xml`
- When a scrap has **no photo**, the image area shows a 📷 **"Add Photo"** overlay
- Only visible to the scrap **owner** (not other users)
- Tapping it triggers `onAddPhotoClick` callback (wire to edit flow)

---

### 3. 🗺️ Map Pin Drop + Manual Location in Upload
- File: `app/src/main/res/layout/fragment_upload.xml`
- File: `app/src/main/java/.../ui/upload/UploadFragment.kt`

Three ways to set location:
- **📡 Detect** — GPS auto-detect (existing)
- **🗺️ Drop Pin** — opens inline map, tap anywhere to set pin (draggable)
- **Type manually** — text field: e.g. "Dharwad, Karnataka"

Priority: GPS/Pin coordinates > manual text entry
If only manual text is given, location name is saved (coords = 0,0)

---

## Other Improvements
- "Add Photo" hidden for other users' scraps (only shows on owner's own)
- Upload layout now cleaner with grouped location section
- Pin marker is draggable for fine-tuning
- Reverse geocoding updates the manual text field automatically after pin drop
- Location section shows helpful hint text when map is open
- Gemini multi-turn conversation (same as before with Claude)
