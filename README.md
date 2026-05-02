# Hami-GP - Parental Control App

## Introduction
Hami is a mobile application designed to help parents monitor their children's online safety using AI-driven text analysis. The app detects harmful content like cyberbullying and harassment in Arabic messages, while preserving privacy through on-device processing. aiming to provide Arabic-speaking families with a privacy-focused digital safety tool that detects harmful text in real time.


## Technologies Used
- **Language:** Kotlin
- **Platform:** Android 
- **AI Framework:** TensorFlow Lite
- **Backend:** Firebase Authentication


 ## Model Setup
- If the model doesn't load, paste the `.tflite` file into:
app/src/main/assets/


## Launch Instructions
1. Run on device/emulator
2. **Sign up / Log in** as Parent or Child

## Enable Accessibility Service (Child Device)
1. Open Hami app
2. Click **"تفعيل الحماية"**
3. Go to **Settings → Accessibility → Hami**
4. Toggle service **ON**


 ## Test the App
- Parent logs in to view dashboard
- On child device, open any app (browser, notes, etc.) and type an Arabic threat word like `اكرهك` or `غبي`
- Check Android Studio logs — you should see successful model detection


 
## Team Members
see `AUTHORS` file for contributors.
