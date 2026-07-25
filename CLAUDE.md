# FixUp — FYP Project Master Brief
> Read this file at the start of EVERY Claude Code session before writing any code.

---

## What This Is
A two-sided Android marketplace app connecting homeowners with verified service
providers in Pakistan. The core jury demo flow is:

**Customer photographs a damaged wall → AI detects damage type → service category
is auto-suggested → price is estimated using AI + market data → request is posted
→ vendors bid → customer accepts best bid.**

This is a jury demo, not a production app. Prioritize a smooth working demo flow
over edge case handling or advanced features.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Mobile App | Android (Kotlin) |
| Database | Firebase Firestore |
| Auth | Firebase Authentication |
| File Storage | Firebase Storage |
| Backend API | Python Flask |
| AI Damage Model | YOLOv8n-cls (single classification model, 5 classes) |
| Price Estimation | Grok API + Excel base data (pandas) |
| Chatbot | Grok API (context-aware, FixUp-specific) |
| Backend Hosting | Hugging Face Spaces (free, always on) |
| App Distribution | Direct APK / Google Play Store |

---

## Project Folder Structure

```
FixUp/
├── CLAUDE.md
├── android-app/                            ← open in Android Studio
│   └── app/src/main/java/com/fixup/
│       ├── auth/
│       │   ├── SplashActivity.kt
│       │   ├── LoginActivity.kt
│       │   └── RegisterActivity.kt
│       ├── customer/
│       │   ├── CustomerHomeActivity.kt
│       │   ├── PostRequestActivity.kt
│       │   ├── BidsActivity.kt
│       │   ├── RequestStatusActivity.kt
│       │   └── RateVendorActivity.kt
│       ├── vendor/
│       │   ├── VendorDashboardActivity.kt
│       │   └── RequestDetailActivity.kt
│       ├── admin/
│       │   ├── AdminDashboardActivity.kt
│       │   ├── ManageVendorsActivity.kt
│       │   └── AllRequestsActivity.kt
│       ├── shared/
│       │   ├── ChatActivity.kt             ← customer ↔ vendor chat
│       │   └── ChatbotActivity.kt          ← AI assistant chatbot
│       └── utils/
│           ├── Constants.kt
│           ├── ImageUtils.kt
│           └── NetworkUtils.kt
├── backend/
│   ├── app.py
│   ├── model_inference.py
│   ├── price_lookup.py
│   ├── chatbot.py                          ← Grok chatbot logic
│   ├── bids_data.xlsx
│   ├── requirements.txt
│   └── model_weights/
│       └── best.pt
└── ml-model/
    └── train_fixup_yolov8.ipynb
```

---

## User Roles

| Role | Value | Access |
|---|---|---|
| Normal Customer | `"customer"` | Post requests, view bids, chat, rate, use chatbot |
| Service Vendor | `"vendor"` | View requests, submit bids, chat, complete jobs |
| Admin | `"admin"` | Dashboard, manage vendors, view all data |

### Registration Rules
- Shared RegisterActivity: "I need services" (customer) vs "I provide services" (vendor)
- Vendor selects service category on registration
- **Vendors auto-approved on registration** — isActive defaults to true
- Admin has NO registration — one hardcoded account in Firebase Console
- SplashActivity routes by role:
  - `customer` → CustomerHomeActivity
  - `vendor` + isActive true → VendorDashboardActivity
  - `vendor` + isActive false → suspended message screen
  - `admin` → AdminDashboardActivity

---

## Demo Accounts (Create Manually in Firebase Console)

| Role | Email | Password | Service Category |
|---|---|---|---|
| Admin | admin@fixup.pk | Admin@123 | — |
| Vendor 1 | mason@fixup.pk | Vendor@123 | Plastering / Mason |
| Vendor 2 | electrician@fixup.pk | Vendor@123 | Electrician |
| Vendor 3 | painter@fixup.pk | Vendor@123 | Painter |
| Customer | customer@fixup.pk | Customer@123 | — |

After creating via Firebase Auth Console, manually add Firestore document under
users/{uid} with role, serviceCategory, isActive: true, rating: 0, totalJobs: 0.
Pre-seed 2-3 completed requests + bids so admin dashboard is not empty.

---

## Firebase Firestore Schema

```
users/
  {uid}/
    name:            string
    email:           string
    phone:           string
    role:            "customer" | "vendor" | "admin"
    city:            string        (default: "Karachi")
    isActive:        boolean       (default: true)
    serviceCategory: string        (vendors only)
    rating:          number        (vendors only, default: 0.0)
    totalJobs:       number        (vendors only, default: 0)
    createdAt:       timestamp

serviceRequests/
  {requestId}/
    customerId:           string
    customerName:         string
    title:                string
    description:          string
    imageUrl:             string
    detectedClass:        string
    detectedCategory:     string
    detectedConfidence:   number
    estimatedPriceMin:    number
    estimatedPriceMax:    number
    estimatedPriceRecommended: number
    priceJustification:   string
    priceSampleCount:     number
    city:                 string
    status:               "open" | "assigned" | "completed"
    acceptedBidId:        string
    acceptedVendorId:     string
    createdAt:            timestamp
    completedAt:          timestamp (nullable)

bids/
  {bidId}/
    requestId:       string
    vendorId:        string
    vendorName:      string
    vendorRating:    number
    vendorCategory:  string
    amount:          number
    estimatedHours:  number
    note:            string
    status:          "pending" | "accepted" | "rejected"
    createdAt:       timestamp

chats/
  {chatId}/                   (chatId == requestId)
    messages/
      {msgId}/
        senderId:    string
        senderName:  string
        text:        string
        timestamp:   timestamp

ratings/
  {ratingId}/
    requestId:   string
    customerId:  string
    vendorId:    string
    score:       number   (1 to 5)
    comment:     string
    createdAt:   timestamp
```

---

## AI Damage Model — Finalized 5 Classes

| Detected Class | Mapped Service | Icon | Dataset |
|---|---|---|---|
| `wall_crack` | Plastering / Mason | 🧱 | BD3 Major + Minor Crack (~1200 imgs) |
| `peeling_paint` | Painter | 🎨 | BD3 Peeling (~600 imgs) |
| `spalling` | Plastering / Mason | 🧱 | BD3 Spalling (~600 imgs) |
| `algae` | Plumber / Mason | 💧 | BD3 Algae (~600 imgs) |
| `electrical_fault` | Electrician | ⚡ | Electrical hazards dataset (~600 imgs) |

Total: ~3600 images → ~10800 after 3x augmentation

### Dropped Classes
- ❌ `broken_tile` — no quality dataset
- ❌ `water_stain` / `stain` — BD3 stain is food/graffiti not water damage
- ❌ `plain` / `normal` — background class not needed

### electrical_fault Note
Original dataset had extension wire images causing spalling confusion.
Fixed by mixing 60-80 burned wall socket images in. Total capped at 600.
Safety override: if spalling vs electrical gap < 20%, always prefer electrical_fault.

---

## Flask Backend Code

### model_inference.py
```python
from ultralytics import YOLO
from PIL import Image
import io

model = YOLO("model_weights/best.pt")

CATEGORY_MAP = {
    "wall_crack":       {"service": "Plastering / Mason", "icon": "🧱"},
    "peeling_paint":    {"service": "Painter",            "icon": "🎨"},
    "spalling":         {"service": "Plastering / Mason", "icon": "🧱"},
    "algae":            {"service": "Plumber / Mason",    "icon": "💧"},
    "electrical_fault": {"service": "Electrician",        "icon": "⚡"},
}

def predict(image_bytes):
    img     = Image.open(io.BytesIO(image_bytes))
    results = model(img)
    probs   = results[0].probs
    names   = results[0].names

    top1_name = names[probs.top1]
    top1_conf = probs.top1conf.item()
    top2_name = names[probs.top5[1]]
    top2_conf = probs.top5conf[1].item()

    # Safety override: spalling vs electrical close call → prefer electrical
    if top1_name == "spalling" and top2_name == "electrical_fault":
        if (top1_conf - top2_conf) < 0.20:
            top1_name = "electrical_fault"
            top1_conf = top2_conf

    if top1_conf < 0.50:
        return {"detected": top1_name, "service": "General Handyman",
                "icon": "🔧", "confidence": round(top1_conf, 2), "fallback": True}

    mapped = CATEGORY_MAP.get(top1_name, {"service": "General Handyman", "icon": "🔧"})
    return {"detected": top1_name, "service": mapped["service"],
            "icon": mapped["icon"], "confidence": round(top1_conf, 2), "fallback": False}
```

### price_lookup.py
```python
import pandas as pd
df = pd.read_excel("bids_data.xlsx")

def get_base_price(service_category, city="Karachi"):
    row = df[(df["service_category"] == service_category) & (df["city"] == city)]
    if row.empty:
        return {"min": 500, "max": 5000, "mean": 2000, "sample_count": 0}
    return {
        "min":          int(row["min_price"].values[0]),
        "max":          int(row["max_price"].values[0]),
        "mean":         int(row["mean_price"].values[0]),
        "sample_count": int(row["sample_count"].values[0])
    }
```

### Grok price estimation (inside app.py or separate grok_helper.py)
```python
import requests, json, os

GROK_API_KEY = os.environ.get("GROK_API_KEY")  # set as HuggingFace secret

def get_ai_price_estimate(detected_class, confidence, description, city, base_price):
    prompt = f"""You are a Pakistani home repair pricing expert in {city}.

Damage detected: {detected_class.replace('_', ' ')} (AI confidence: {confidence:.0%})
Customer description: "{description}"
Market base range from recent bids: PKR {base_price['min']} – {base_price['max']}

Give a realistic price estimate considering damage severity from the description,
typical {city} labor costs, and material costs.

Respond ONLY in this exact JSON format, no extra text:
{{
  "min": 1200,
  "max": 2800,
  "recommended": 1800,
  "justification": "one sentence reason"
}}"""

    try:
        res = requests.post(
            "https://api.x.ai/v1/chat/completions",
            headers={"Authorization": f"Bearer {GROK_API_KEY}",
                     "Content-Type": "application/json"},
            json={"model": "grok-beta",
                  "messages": [{"role": "user", "content": prompt}],
                  "temperature": 0.3},
            timeout=10
        )
        content = res.json()["choices"][0]["message"]["content"]
        return json.loads(content)
    except:
        # Always fallback to Excel if Grok fails — never crash
        return {"min": base_price["min"], "max": base_price["max"],
                "recommended": base_price["mean"],
                "justification": f"Based on {base_price['sample_count']} recent bids in {city}"}
```

### chatbot.py — FixUp-specific AI assistant
```python
import requests, json, os

GROK_API_KEY = os.environ.get("GROK_API_KEY")

SYSTEM_PROMPT = """You are FixUp Assistant, a helpful chatbot for the FixUp home services app in Pakistan.
You help customers with home repair problems. You can:
1. Help identify what type of damage they have and which service they need
2. Give honest price advice based on Pakistani market rates (Karachi)
3. Help them choose between vendor bids (advise on rating vs price tradeoff)
4. Triage urgency — tell them if something needs immediate attention
5. Give post-repair guidance if problems persist

Market rates reference (Karachi):
- Plastering / Mason: PKR 800–3500
- Electrician: PKR 600–4000
- Painter: PKR 2000–12000
- Plumber / Mason: PKR 500–2500
- General Handyman: PKR 500–3000

Always respond in the same language the user writes in (Urdu or English).
Keep responses concise — max 3-4 sentences. Be friendly and practical.
Never recommend specific vendors by name. Never discuss topics unrelated to home repair."""

def chat(messages):
    # messages = list of {"role": "user"/"assistant", "content": "..."}
    try:
        res = requests.post(
            "https://api.x.ai/v1/chat/completions",
            headers={"Authorization": f"Bearer {GROK_API_KEY}",
                     "Content-Type": "application/json"},
            json={"model": "grok-beta",
                  "messages": [{"role": "system", "content": SYSTEM_PROMPT}] + messages,
                  "temperature": 0.7},
            timeout=15
        )
        return res.json()["choices"][0]["message"]["content"]
    except:
        return "Sorry, I'm having trouble connecting. Please try again in a moment."
```

### app.py — all routes
```python
from flask import Flask, request, jsonify
from model_inference import predict
from price_lookup import get_base_price
from chatbot import chat
import json, os

app = Flask(__name__)

@app.route("/predict", methods=["POST"])
def predict_route():
    if "image" not in request.files:
        return jsonify({"error": "No image"}), 400
    description = request.form.get("description", "No description provided")
    city        = request.form.get("city", "Karachi")
    result      = predict(request.files["image"].read())
    base_price  = get_base_price(result["service"], city)
    ai_price    = get_ai_price_estimate(
                    result["detected"], result["confidence"],
                    description, city, base_price)
    return jsonify({**result,
                    "price_min":         ai_price["min"],
                    "price_max":         ai_price["max"],
                    "price_recommended": ai_price["recommended"],
                    "price_reason":      ai_price["justification"],
                    "sample_count":      base_price["sample_count"]})

@app.route("/chat", methods=["POST"])
def chat_route():
    data     = request.get_json()
    messages = data.get("messages", [])
    if not messages:
        return jsonify({"error": "No messages"}), 400
    reply = chat(messages)
    return jsonify({"reply": reply})

@app.route("/health", methods=["GET"])
def health():
    return jsonify({"status": "ok"})

if __name__ == "__main__":
    app.run(debug=True)
```

### requirements.txt
```
flask
ultralytics
pandas
openpyxl
pillow
requests
```

### bids_data.xlsx seed data
| service_category | city | min_price | max_price | mean_price | sample_count |
|---|---|---|---|---|---|
| Plastering / Mason | Karachi | 800 | 3500 | 1800 | 24 |
| Electrician | Karachi | 600 | 4000 | 1600 | 18 |
| Painter | Karachi | 2000 | 12000 | 5500 | 9 |
| Plumber / Mason | Karachi | 500 | 2500 | 1100 | 31 |
| General Handyman | Karachi | 500 | 3000 | 1500 | 8 |

---

## Chatbot — What It Does (Tell This to Jury)

The chatbot is FixUp-specific, not a generic AI. It does 5 things:

1. **Problem identification** — user describes issue in plain text/Urdu, bot identifies damage type and which service they need
2. **Price advisor** — "is PKR 2500 fair for a hairline crack?" — gives honest market-based answer
3. **Bid selection helper** — user has 3 bids, asks which to pick — bot advises on rating vs price tradeoff
4. **Urgency triage** — "water dripping from ceiling" → bot tells them how urgent it is
5. **Post-repair guidance** — "wall was fixed but still damp" → bot advises next steps

**Where chatbot appears in app:**
- Floating chat button (FAB) on CustomerHomeActivity
- Opens ChatbotActivity — full screen chat UI
- Conversation history maintained within session

**What chatbot does NOT do:**
- Does not recommend specific vendors
- Does not discuss non-home-repair topics
- Does not replace the vendor ↔ customer chat (that is separate)

---

## Android Key Rules

### Constants.kt
```kotlin
object Constants {
    const val COLLECTION_USERS    = "users"
    const val COLLECTION_REQUESTS = "serviceRequests"
    const val COLLECTION_BIDS     = "bids"
    const val COLLECTION_CHATS    = "chats"
    const val COLLECTION_RATINGS  = "ratings"
    const val FLASK_BASE_URL      = "https://YOUR-SPACE.hf.space"
    const val FLASK_PREDICT       = "/predict"
    const val FLASK_CHAT          = "/chat"
    const val ROLE_CUSTOMER       = "customer"
    const val ROLE_VENDOR         = "vendor"
    const val ROLE_ADMIN          = "admin"
    const val DEFAULT_CITY        = "Karachi"
}
```

### ImageUtils.kt
```kotlin
fun compressImage(bitmap: Bitmap): ByteArray {
    val stream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 60, stream)
    return stream.toByteArray()
}
```

### NetworkUtils.kt
```kotlin
fun isNetworkAvailable(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    return cm.activeNetworkInfo?.isConnected == true
}
```

### Always use addSnapshotListener not .get() for:
BidsActivity, CustomerHomeActivity, VendorDashboardActivity, ChatActivity

### AI Result Display Format (PostRequestActivity)
```
🧱 Wall Crack Detected
   Suggested Service: Plastering / Mason
   Confidence: 87%

   💰 Estimated Price: PKR 1,200 – 2,800
   Recommended: PKR 1,800
   "Medium severity crack, 1-2 hours labor"
   Based on market data + AI analysis
```

### Camera Hint Text in PostRequestActivity
```
🧱 Wall/ceiling crack  → photograph crack directly, close up
💧 Moisture / algae    → photograph the affected wall area
⚡ Electrical issue    → photograph damaged socket or wire
🎨 Paint damage        → photograph the peeling area
🧱 Surface spalling    → photograph the crumbling surface
```

---

## Screen Navigation Flow

```
SplashActivity → LoginActivity → RegisterActivity
             └→ CustomerHomeActivity [FAB chatbot button]
             └→ VendorDashboardActivity
             └→ AdminDashboardActivity

CustomerHomeActivity
  ├── FAB → ChatbotActivity (AI assistant)
  ├── Post Request → PostRequestActivity
  │     → AI result + price → BidsActivity
  │           → Accept → ChatActivity → RateVendorActivity
  └── Tap request → RequestStatusActivity

VendorDashboardActivity → RequestDetailActivity → bid submit
                       → ChatActivity → mark complete

AdminDashboardActivity → ManageVendorsActivity
                      → AllRequestsActivity
```

---

## Deployment

| Component | Where | Notes |
|---|---|---|
| Android | Direct APK / Google Play ($25) | Signed APK from Android Studio |
| Flask | Hugging Face Spaces (free) | Push code, set GROK_API_KEY as Space secret |
| Firebase | Already cloud | Nothing to deploy |

**Deploy Flask in Week 1. Set GROK_API_KEY as a secret in HuggingFace Space settings.
Never hardcode the API key. Test /health and /chat from phone before building app.**

---

## What NOT to Build
- ❌ Real payments — "Pay Now" button only
- ❌ GPS tracking
- ❌ NADRA/CNIC verification
- ❌ Urdu voice input
- ❌ iOS
- ❌ Push notifications
- ❌ Admin registration
- ❌ Multiple AI models
- ❌ Generic chatbot — must stay FixUp-specific

---

## File Creation Checklist (Do Before Opening Claude Code)

### Firebase Setup
- [ ] console.firebase.google.com → New project → FixUp
- [ ] Enable Authentication (Email/Password)
- [ ] Enable Firestore Database
- [ ] Enable Storage
- [ ] Add Android app → package: com.fixup → download google-services.json
- [ ] Drop google-services.json into android-app/app/ folder

### Android Studio
- [ ] New Project → Empty Views Activity → Kotlin → com.fixup → Min SDK 24
- [ ] Add Firebase via Tools → Firebase Assistant
- [ ] Create res/layout/ folder (right click res → New → Android Resource Directory → layout)

### Empty Kotlin Files (18 files)
```
com.fixup.auth          → SplashActivity, LoginActivity, RegisterActivity
com.fixup.customer      → CustomerHomeActivity, PostRequestActivity,
                          BidsActivity, RequestStatusActivity, RateVendorActivity
com.fixup.vendor        → VendorDashboardActivity, RequestDetailActivity
com.fixup.admin         → AdminDashboardActivity, ManageVendorsActivity, AllRequestsActivity
com.fixup.shared        → ChatActivity, ChatbotActivity
com.fixup.utils         → Constants, ImageUtils, NetworkUtils
```

Each file:
```kotlin
package com.fixup.auth
import androidx.appcompat.app.AppCompatActivity
class SplashActivity : AppCompatActivity()
```

### Empty XML Layout Files (21 files)
```
activity_splash, activity_login, activity_register,
activity_customer_home, activity_post_request, activity_bids,
activity_request_status, activity_rate_vendor,
activity_vendor_dashboard, activity_request_detail,
activity_admin_dashboard, activity_manage_vendors, activity_all_requests,
activity_chat, activity_chatbot,
item_request, item_bid, item_message_sent, item_message_received,
item_vendor, item_chatbot_message
```

### Register All 15 Activities in AndroidManifest.xml
```xml
<activity android:name=".auth.SplashActivity">
    <intent-filter>
        <action android:name="android.intent.action.MAIN"/>
        <category android:name="android.intent.category.LAUNCHER"/>
    </intent-filter>
</activity>
<activity android:name=".auth.LoginActivity"/>
<activity android:name=".auth.RegisterActivity"/>
<activity android:name=".customer.CustomerHomeActivity"/>
<activity android:name=".customer.PostRequestActivity"/>
<activity android:name=".customer.BidsActivity"/>
<activity android:name=".customer.RequestStatusActivity"/>
<activity android:name=".customer.RateVendorActivity"/>
<activity android:name=".vendor.VendorDashboardActivity"/>
<activity android:name=".vendor.RequestDetailActivity"/>
<activity android:name=".admin.AdminDashboardActivity"/>
<activity android:name=".admin.ManageVendorsActivity"/>
<activity android:name=".admin.AllRequestsActivity"/>
<activity android:name=".shared.ChatActivity"/>
<activity android:name=".shared.ChatbotActivity"/>
```

### Backend Files
```
backend/app.py
backend/model_inference.py
backend/price_lookup.py
backend/chatbot.py
backend/requirements.txt
backend/bids_data.xlsx      (seed with 5 rows above)
backend/model_weights/      (empty folder, best.pt goes here)
```

---

## Claude Code Session Order

| # | Topic | Files |
|---|---|---|
| 1 | Utils | Constants.kt, ImageUtils.kt, NetworkUtils.kt |
| 2 | Auth | SplashActivity, LoginActivity, RegisterActivity |
| 3 | Flask skeleton + HuggingFace deploy | app.py, model_inference.py, price_lookup.py, chatbot.py |
| 4 | Post Request + AI + price | PostRequestActivity + /predict with Grok price |
| 5 | Customer home + bids | CustomerHomeActivity, BidsActivity |
| 6 | Vendor flow | VendorDashboardActivity, RequestDetailActivity |
| 7 | Chat (vendor↔customer) | ChatActivity |
| 8 | Chatbot | ChatbotActivity + /chat endpoint |
| 9 | Ratings + complete | RateVendorActivity |
| 10 | Admin | AdminDashboardActivity, ManageVendorsActivity, AllRequestsActivity |
| 11 | Polish | errors, seed button, UI cleanup, network handling |

Start every session:
> "Read CLAUDE.md. Session [N]. Completed so far: [paste ticked boxes]."

---

## Progress Tracker

### Week 1 — Foundation
- [x] Firebase project created (Auth + Firestore + Storage)
- [x] Android project + google-services.json
- [x] res/layout/ folder created
- [x] 18 empty Kotlin files created
- [x] 21 empty XML files created
- [x] 15 activities registered in AndroidManifest
- [x] Constants.kt done
- [x] ImageUtils.kt done
- [x] NetworkUtils.kt done
- [x] SplashActivity routing working
- [x] LoginActivity working
- [x] RegisterActivity working (customer + vendor + category)
- [x] Flask running locally on port 5000
- [x] bids_data.xlsx seeded
- [x] chatbot.py working locally (test with curl)
- [x] Flask deployed to Hugging Face Spaces
- [x] GROK_API_KEY set as Space secret
- [x] URL in Constants.kt FLASK_BASE_URL
- [x] /health + /chat tested from phone browser

### Week 2 — Core Features
- [x] Roboflow project with 5 classes uploaded
- [x] 3x augmentation applied
- [x] YOLOv8n-cls trained on Colab >70% accuracy
- [x] best.pt on Hugging Face Space
- [x] /predict returning real output with Grok price
- [ ] PostRequestActivity — full flow working
- [x] AI result + price display correct
- [ ] CustomerHomeActivity real-time list
- [ ] VendorDashboardActivity filtered real-time
- [ ] RequestDetailActivity + bid submit
- [ ] BidsActivity real-time + accept logic
- [ ] ChatbotActivity — working end to end
- [ ] Chatbot tested: price advice, urgency triage, bid help

### Week 3 — Polish + Admin + Demo
- [ ] ChatActivity real-time both directions
- [ ] Vendor mark complete
- [ ] RateVendorActivity + avg rating update
- [ ] AdminDashboardActivity stats
- [ ] ManageVendors suspend toggle
- [ ] AllRequestsActivity
- [ ] Seed Demo Data button
- [ ] Network error handling everywhere
- [ ] Demo accounts in Firebase Console
- [ ] Pre-seeded data in Firestore
- [ ] Full flow tested on real phone
- [ ] AI tested with Pakistani home photos
- [ ] Signed APK ready

---

## Jury Demo Script

**Setup:** Phone 1 = customer, Phone 2 = mason vendor, Laptop = admin

1. Phone 1 → Post Request → photograph cracked wall
2. AI result: *"🧱 Wall Crack | Plastering/Mason | 87% | PKR 1,200–2,800 | Recommended PKR 1,800"*
3. Submit → Phone 2 gets request instantly
4. Vendor bids PKR 1,500
5. Phone 1 bid appears instantly → accept
6. Chat opens on both
7. Show chatbot: type *"is PKR 1500 a fair price for a wall crack?"* → smart response
8. Laptop → admin dashboard → suspend a vendor
9. Done under 4 minutes

**Line about AI pricing:**
*"Instead of a static price table, our system combines real bid data from the platform
with a Grok AI call that factors in damage severity from the customer's description —
giving a dynamic, context-aware estimate rather than a fixed range."*

**Line about chatbot:**
*"The chatbot is FixUp-specific — it helps users identify problems, gives honest price
advice based on market data, helps choose between bids, and triages emergencies.
It responds in both Urdu and English."*

---

*Last updated: Grok price API added, FixUp-specific chatbot added with 5 use cases,
file counts updated (18 Kotlin, 21 XML, 15 activities).*
