#!/usr/bin/env python3
"""Runs after `npx cap add android`.
Adds: app icons, the home-screen widget (Java + resources), notification permissions."""
import glob, json, os, shutil, sys

def fail(msg):
    print("ERROR:", msg)
    sys.exit(1)

cfg = json.load(open("capacitor.config.json", encoding="utf-8"))
pkg = cfg["appId"]
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
permissions = (
    '<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>\n'
    '    <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM"/>\n'
    '    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED"/>\n'
    '    '
)
receivers = (
    '    <receiver android:name=".HerbWidgetProvider" android:exported="true">\n'
    '            <intent-filter>\n'
    '                <action android:name="android.appwidget.action.APPWIDGET_UPDATE"/>\n'
    '            </intent-filter>\n'
    '            <meta-data android:name="android.appwidget.provider" android:resource="@xml/herb_widget_info"/>\n'
    '        </receiver>\n'
    '        <receiver android:name=".ReadyReceiver" android:exported="false"/>\n'
    '        <receiver android:name=".ReplantReceiver" android:exported="false"/>\n'
    '        <receiver android:name=".BootReceiver" android:exported="true">\n'
    '            <intent-filter>\n'
    '                <action android:name="android.intent.action.BOOT_COMPLETED"/>\n'
    '                <action android:name="android.intent.action.MY_PACKAGE_REPLACED"/>\n'
    '            </intent-filter>\n'
    '        </receiver>\n'
    '    '
)
if "POST_NOTIFICATIONS" not in manifest:
    manifest = manifest.replace("<application", permissions + "<application", 1)
if "HerbWidgetProvider" not in manifest:
    manifest = manifest.replace("</application>", receivers + "</application>", 1)
for needed in ("POST_NOTIFICATIONS", "RECEIVE_BOOT_COMPLETED", "HerbWidgetProvider", "ReadyReceiver", "ReplantReceiver", "BootReceiver"):
    if needed not in manifest:
        fail("could not patch AndroidManifest.xml (" + needed + ")")
open(manifest_path, "w", encoding="utf-8").write(manifest)

print("Icons, widget, notifications and permissions applied for", pkg)
