## fort.ind on android !!!

Built-in Kotlin fort.ind for Android is a 1:1 recreation of Fort.inds site, but directly into an Android app (natively obv) 
currently targets android 16

## bulid it! 
wanna test it out? grab one of the releases or bulid it 
## requirments 
- Java JDK 11 
- Android SDK with API level 37  (device/emulator must run Android API 24 or newer)
- Android Studio (recommended) or Android SDK + adb
- Internet access to download dependencies the first time
Quick CLI build (Unix/macOS)
```bash
git clone https://github.com/fort-ind/android.git
cd android
# build an apk
./gradlew assembleDebug

# install to a connected device or running emulator
./gradlew installDebug
```

Quick CLI build (Windows)
```powershell
git clone https://github.com/fort-ind/android.git
cd android
# build an apk
gradlew.bat assembleDebug

# install to a connected device or running emulator
gradlew.bat installDebug
```
