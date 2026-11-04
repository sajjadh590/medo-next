@echo off
setlocal

REM Signs an unsigned release APK locally on the owner's Windows PC without uploading any keystore.
set UNSIGNED_APK=%~1
if "%UNSIGNED_APK%"=="" set UNSIGNED_APK=app-release-unsigned.apk
set SIGNED_APK=%~2
if "%SIGNED_APK%"=="" set SIGNED_APK=medo-next-signed.apk

if "%MEDO_KEYSTORE_PATH%"=="" set MEDO_KEYSTORE_PATH=%USERPROFILE%\.medo\release.keystore
if "%MEDO_KEY_ALIAS%"=="" set MEDO_KEY_ALIAS=medo

if not exist "%UNSIGNED_APK%" (
  echo Error: Unsigned APK not found: %UNSIGNED_APK%
  exit /b 1
)

call apksigner sign --ks "%MEDO_KEYSTORE_PATH%" --ks-key-alias "%MEDO_KEY_ALIAS%" --out "%SIGNED_APK%" "%UNSIGNED_APK%"
call apksigner verify --verbose "%SIGNED_APK%"
echo Signed APK written to %SIGNED_APK%
