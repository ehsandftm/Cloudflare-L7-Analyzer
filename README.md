# 🚀 Advanced L7 Cloudflare Scanner & Analyzer (Android)
### آنالیزور و اسکنر پیشرفته لایه ۷ کلودفلر (نسخه اندروید)

---

## 🌍 English Description
A high-performance Android application designed to discover and analyze Cloudflare nodes using **Layer 7 (Handshake 101)** verification. Unlike simple ping tools, this app performs deep stress testing to ensure the stability of V2Ray/VLESS connections.

### ✨ Key Features
- **L7 Deep Analysis:** Verifies connections up to the SSL/WS handshake level.
- **100-Step Stress Test:** Measures real-world stability with 10 batches of 10 concurrent requests.
- **Intelligent Classification:** Automatically labels IPs as **Gaming Grade** (Low Jitter), **Stream Ready**, or **Average**.
- **Massive Parallel Scanning:** Utilizes a semaphore-based multi-threading system (100+ threads).
- **Direct Integration:** One-click copy and connect for **NetMod Syna** and **v2rayNG**.
- **Automated CIDR Expansion:** Scans entire IP ranges (e.g., /20) with a single input.

---

## 🇮🇷 توضیحات فارسی
این اپلیکیشن اندرویدی ابزاری قدرتمند برای یافتن و تحلیل آی‌پی‌های تمیز کلودفلر با استفاده از روش **تست لایه ۷ (Handshake 101)** است. برخلاف ابزارهای پینگ ساده، این برنامه با شبیه‌سازی دقیق اتصال وی‌پی‌ان، پایداری واقعی را می‌سنجد.

### ✨ قابلیت‌های کلیدی:
- **آنالیز عمیق لایه ۷:** بررسی اتصال تا مرحله نهایی هندشیک SSL و WebSocket.
- **تست استرس ۱۰۰ مرحله‌ای:** محاسبه دقیق پکت‌لاست (Packet Loss) و جیتر در ۱۰ دسته ۱۰ تایی.
- **دسته‌بندی هوشمند:** تشخیص خودکار آی‌پی‌های مناسب برای **گیمینگ** (تأخیر بسیار کم) و **استریم**.
- **اسکن موازی فوق‌سریع:** استفاده از سیستم Semaphore برای مدیریت بیش از ۱۰۰ رشته همزمان.
- **اتصال مستقیم:** قابلیت کپی هوشمند و باز کردن خودکار در اپلیکیشن‌های **NetMod Syna** و **v2rayNG**.
- **پشتیبانی از رنج آی‌پی:** قابلیت تبدیل خودکار CIDR (مثل /20) به لیست آی‌پی برای اسکن انبوه.

---

<img width="490" height="1024" alt="image" src="https://github.com/user-attachments/assets/ca94ab0a-7049-4d40-bc90-43836da62d6f" />


---

## 📥 How to Install / نحوه نصب
1. Go to the **Releases** section on the right side of this page.
2. Download the latest `.apk` file.
3. Install it on your Android device.

۱. به بخش **Releases** در سمت راست همین صفحه بروید.
۲. آخرین فایل با پسوند `.apk` را دانلود کنید.
۳. آن را روی گوشی اندرویدی خود نصب کنید.

---

🛠 Setup Guide (For Beginners)

To start scanning, you must enter your personal server details in the INPUT tab:

SNI (Server Name Indication): Enter your subdomain address or Worker URL here.

Path: Enter the Path information exactly as it appears in your v2ray configuration.

UUID: Paste your VLESS config's unique identifier. This code is required for connection authentication.

Note: After entering the details, make sure to click the SAVE SETTINGS button to store them for future scans.


🇮🇷 راهنمای تنظیمات (برای کاربران مبتدی)
برای شروع اسکن، باید اطلاعات سرور شخصی خود را در تب INPUT وارد کنید:

SNI (Server Name Indication): 
در این قسمت آدرس ساب دامین یا ورکر را وارد کنید
Path: 
در این قسمت اطلاعاتی که در کانفیگ v2ray را زدید وارد کنید
UUID:
 کد شناسایی اختصاصی کانفیگ VLESS خود را در این کادر کپی کنید. این کد برای احراز هویت اتصال شماست.

نکته: پس از وارد کردن اطلاعات، حتماً دکمه SAVE SETTINGS را بزنید تا تنظیمات برای اسکن‌های بعدی ذخیره .


## 🛠 Tech Stack
- **Language:** Kotlin
- **UI:** Jetpack Compose
- **Concurrency:** Kotlin Coroutines & Semaphores
- **Network:** Java/Kotlin SSL Sockets (L7)

---
*Developed with ❤️ for a free and stable internet.*
حقوق معنوی این نرم افزار برای من محفوظ می باشد.
