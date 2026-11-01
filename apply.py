#!/usr/bin/env python3
"""Runs after `npx cap add android`.
Adds: app icons, the home-screen widget (Java + resources), notification permissions."""
import glob, json, os, shutil, sys

def fail(msg):
    print("ERROR:", msg)
    sys.exit(1)

try:
    pkg = json.load(open("capacitor.config.json", encoding="utf-8"))["appId"]
except Exception:
    pkg = "com.example.herbloretracker"
main = "android/app/src/main"
res = main + "/res"
manifest_path = main + "/AndroidManifest.xml"

if not os.path.isfile(manifest_path):
    fail(manifest_path + " not found - did 'npx cap add android' run?")

required = [
    "android-res/mipmap-xxxhdpi/ic_launcher.png",
    "android-res/mipmap-xxxhdpi/ic_launcher_foreground.png",
    "android-res/mipmap-anydpi-v26/ic_launcher.xml",
    "android-res/values/herb_icon_colors.xml",
    "android-res/drawable-xxxhdpi/ic_stat_herb.png",
    "android-extra/java/MainActivity.java",
    "android-extra/java/HerbWidgetPlugin.java",
    "android-extra/java/HerbWidgetProvider.java",
    "android-extra/java/HerbStore.java",
    "android-extra/java/HerbNotifier.java",
    "android-extra/java/ReadyReceiver.java",
    "android-extra/java/ReplantReceiver.java",
    "android-extra/java/BootReceiver.java",
    "android-extra/java/SnoozeReceiver.java",
    "android-extra/java/HerbMiniWidgetProvider.java",
    "android-extra/offline_assets.py",
    "android-extra/res/layout/herb_widget_mini.xml",
    "android-extra/res/xml/herb_widget_mini_info.xml",
    "android-extra/res/layout/herb_widget.xml",
    "android-extra/res/xml/herb_widget_info.xml",
    "android-extra/res/drawable/widget_bg.xml",
    "android-extra/res/drawable/widget_btn_bg.xml",
    "android-extra/res/values/herb_widget_strings.xml",
]
for f in required:
    if not os.path.isfile(f):
        fail("MISSING FILE: %s - upload the android-res and android-extra folders again" % f)

# 1. icons: remove default icons so ours win, then copy ours in
for f in glob.glob(res + "/mipmap-*/ic_launcher*.webp"):
    os.remove(f)
shutil.copytree("android-res", res, dirs_exist_ok=True)

# 2. widget resources
shutil.copytree("android-extra/res", res, dirs_exist_ok=True)

# 3. widget Java code (MainActivity.java replaces the default one)
java_dir = os.path.join(main, "java", *pkg.split("."))
os.makedirs(java_dir, exist_ok=True)
for src in glob.glob("android-extra/java/*.java"):
    text = open(src, encoding="utf-8").read()
    text = text.replace("package com.example.herbloretracker;", "package %s;" % pkg)
    with open(os.path.join(java_dir, os.path.basename(src)), "w", encoding="utf-8") as out:
        out.write(text)

# 4. manifest: permissions + receivers (widget, notifications, boot)
manifest = open(manifest_path, encoding="utf-8").read()
receivers = (
    '    <receiver android:name=".HerbWidgetProvider" android:exported="true" android:label="Herb Timer - next ready">\n'
    '            <intent-filter>\n'
    '                <action android:name="android.appwidget.action.APPWIDGET_UPDATE"/>\n'
    '            </intent-filter>\n'
    '            <meta-data android:name="android.appwidget.provider" android:resource="@xml/herb_widget_info"/>\n'
    '        </receiver>\n'
    '        <receiver android:name=".ReadyReceiver" android:exported="false"/>\n'
    '        <receiver android:name=".ReplantReceiver" android:exported="false"/>\n'
    '        <receiver android:name=".SnoozeReceiver" android:exported="false"/>\n'
    '        <receiver android:name=".HerbMiniWidgetProvider" android:exported="true" android:label="Herb Timer - all done (1x1)">\n'
    '            <intent-filter>\n'
    '                <action android:name="android.appwidget.action.APPWIDGET_UPDATE"/>\n'
    '            </intent-filter>\n'
    '            <meta-data android:name="android.appwidget.provider" android:resource="@xml/herb_widget_mini_info"/>\n'
    '        </receiver>\n'
    '        <receiver android:name=".BootReceiver" android:exported="true">\n'
    '            <intent-filter>\n'
    '                <action android:name="android.intent.action.BOOT_COMPLETED"/>\n'
    '                <action android:name="android.intent.action.MY_PACKAGE_REPLACED"/>\n'
    '            </intent-filter>\n'
    '        </receiver>\n'
    '    '
)
for permission in ("POST_NOTIFICATIONS", "SCHEDULE_EXACT_ALARM", "USE_EXACT_ALARM", "RECEIVE_BOOT_COMPLETED"):
    if "android.permission." + permission not in manifest:
        manifest = manifest.replace(
            "<application",
            '<uses-permission android:name="android.permission.%s"/>\n    <application' % permission, 1)
if "HerbWidgetProvider" not in manifest:
    manifest = manifest.replace("</application>", receivers + "</application>", 1)
for needed in ("POST_NOTIFICATIONS", "USE_EXACT_ALARM", "RECEIVE_BOOT_COMPLETED", "HerbWidgetProvider", "ReadyReceiver", "ReplantReceiver", "SnoozeReceiver", "HerbMiniWidgetProvider", "BootReceiver"):
    if needed not in manifest:
        fail("could not patch AndroidManifest.xml (" + needed + ")")
open(manifest_path, "w", encoding="utf-8").write(manifest)

# 5. app version (single source: APP_VERSION in www/index.html)
import re
version = "1.0"
try:
    html_text = open("www/index.html", encoding="utf-8").read()
    version = re.search(r"APP_VERSION\s*=\s*'([^']+)'", html_text).group(1)
except Exception:
    print("WARNING: APP_VERSION not found in www/index.html, using", version)
parts = (version.split(".") + ["0", "0"])[:3]
try:
    code = int(parts[0]) * 10000 + int(parts[1]) * 100 + int(parts[2])
except ValueError:
    code = 1
patched = False
for name in ("build.gradle", "build.gradle.kts"):
    path = os.path.join("android", "app", name)
    if os.path.isfile(path):
        gradle = open(path, encoding="utf-8").read()
        gradle = re.sub(r"(versionCode\s*=?\s*)\d+", r"\g<1>%d" % code, gradle, count=1)
        gradle = re.sub(r'(versionName\s*=?\s*)"[^"]*"', r'\g<1>"%s"' % version, gradle, count=1)
        open(path, "w", encoding="utf-8").write(gradle)
        patched = True
print("App version set to %s (build number %d)%s" % (version, code, "" if patched else " - WARNING: build.gradle not found"))

# 5b. permanent signing key: every build is signed the same way, so a new APK installs over the old one
#     (and keeps your timers) instead of Android refusing it as a "conflicting" app.
keystore = os.path.join("android-extra", "herb.keystore")
if not os.path.isfile(keystore):
    print("WARNING: android-extra/herb.keystore is missing - this build gets a temporary key, so it can't update an older install")
else:
    path = os.path.join("android", "app", "build.gradle")
    if not os.path.isfile(path):
        print("WARNING: android/app/build.gradle not found - could not set the signing key")
    else:
        gradle = open(path, encoding="utf-8").read()
        if "herb.keystore" not in gradle:
            block = (
                "\n    signingConfigs {\n"
                "        debug {\n"
                "            storeFile file('../../android-extra/herb.keystore')\n"
                "            storePassword 'android'\n"
                "            keyAlias 'androiddebugkey'\n"
                "            keyPassword 'android'\n"
                "        }\n"
                "    }\n"
            )
            if re.search(r"^android\s*\{", gradle, re.M):
                gradle = re.sub(r"^(android\s*\{)", lambda m: m.group(1) + block, gradle, count=1, flags=re.M)
                open(path, "w", encoding="utf-8").write(gradle)
                print("Signing key set (android-extra/herb.keystore)")
            else:
                print("WARNING: could not find the android { } block in build.gradle - signing key not set")

print("Icons, widget, notifications and permissions applied for", pkg)

# 6. make the app work offline: bundle the wiki images and fonts into the app
try:
    sys.path.insert(0, "android-extra")
    import offline_assets
    offline_assets.main()
except Exception as error:   # never stop the build because of this
    print("WARNING: offline assets step failed:", error)

# Remember that everything above worked (capacitor.config.js uses this to avoid doing it twice)
open(os.path.join("android", ".herb-applied"), "w").write("ok\n")
