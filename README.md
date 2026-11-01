## project zenith!
a version of fort.android made in jetpack compose using material 3 expressive (in alpha)

## bulid it! 
wanna test it out? grab one of the releases or bulid it 
## requirments 
- Java JDK 11 
- Android SDK with API level 37  (device/emulator must run Android API 27 or newer)
- Android Studio (recommended) or Android SDK + adb
- Internet access to download dependencies the first time
Quick CLI build (Linux/OS X)
```bash
git clone https://git.thesillysociety.net/fort.ind/android.git
cd android
# build an apk
./gradlew assembleDebug
# or..
./gradlew assembleRelease

# install to a connected device or running emulator
./gradlew installDebug
```

Quick CLI build (Windows)
```powershell
git clone https://git.thesillysociety.net/fort.ind/android.git
cd android
# build an apk
gradlew.bat assembleDebug
# or..
gradlew.bat assembleRelease