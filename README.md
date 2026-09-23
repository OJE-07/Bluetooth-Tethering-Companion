# Bluetooth Tethering Companion

Two small Android apps designed to work together when one Android phone shares mobile data to another phone over Bluetooth tethering.

## The two apps

**Phone Master — Host — v1.0.14**

Install this on the phone that has the SIM/mobile-data connection. When Phone Master's **Automatic Bluetooth tethering** switch is enabled, mobile data is the master condition:

- Mobile data ON: Phone Master keeps Bluetooth ON and keeps Bluetooth tethering ON.
- If Bluetooth is switched off while mobile data remains on, Phone Master turns Bluetooth back on and restores tethering.
- If Bluetooth tethering is switched off while mobile data remains on, Phone Master restores it.
- Mobile data OFF: Phone Master becomes passive. It does not force Bluetooth or tethering on or off.
- The optional Bluetooth-status-icon switch can hide the native Bluetooth status-bar icon on compatible Android builds.

**Tethering — Receiver — v1.0.9**

Install this on the phone that receives internet from the host. It does not automatically connect to the host. It watches the receiver's Bluetooth/tethering/internet state and shows a notification telling the user what to do:

- **Turn on Bluetooth and Connect to Tethering**
- **Connect to Tethering**
- **No Internet connection**
- **Internet access**

The two apps do not replace Android's Bluetooth pairing or PAN connection controls.

## Tested environment and compatibility

These builds were developed and tested around Android 9 / XOS / MediaTek devices. Both apps have **minSdk 24 (Android 7.0)** and deliberately use **targetSdk 25** because the working tethering path depends on legacy Android behaviour and manufacturer-specific tethering implementation.

They are sideloaded utilities, not Play Store builds. Other Android versions and manufacturers may behave differently. In particular, background startup, Bluetooth tethering controls and battery management are heavily customized by manufacturers.

## Before installing

You need:

1. An Android **host** phone with working mobile data and Bluetooth tethering.
2. An Android **receiver** phone with Bluetooth PAN/client support.
3. The two APKs: Phone Master v1.0.14 for the host and Tethering v1.0.9 for the receiver.
4. A computer with **ADB / Android Platform Tools** for Phone Master's one-time secure-settings permission if you want Bluetooth-icon hiding.
5. **Termux** and **Termux:Boot** are strongly recommended on devices whose manufacturer blocks normal boot receivers. They provide an extra wake-up path after reboot.

## 1. Pair the phones first

Do this before relying on either app.

1. Turn Bluetooth on on both phones.
2. Open Android Bluetooth settings on both phones.
3. Pair the host and receiver normally and accept the same pairing code on both.
4. Confirm both phones show each other as paired.
5. On the host, manually enable **Bluetooth tethering** once.
6. On the receiver, open the paired host's Bluetooth options and enable the option used for **Internet access**, **Use for Internet**, **PAN**, or similar wording.
7. Confirm the receiver can access the internet through the host.

The exact wording varies by Android manufacturer. These apps assume Android's underlying Bluetooth pairing/PAN relationship already works.

## 2. Install Phone Master on the host

Install the Phone Master v1.0.14 APK and open it.

### Required Android special access

Phone Master needs Android's **Modify system settings** access on the tested XOS/MediaTek tethering stack. Use the button inside Phone Master to open the correct Android settings page, then allow Phone Master to modify system settings.

This is separate from the optional ADB permission below.

### Optional Bluetooth status-icon hiding

The icon-hiding feature writes Android's secure `icon_blacklist` setting and therefore needs the privileged `WRITE_SECURE_SETTINGS` permission. Android does not provide a normal on-screen permission dialog for this.

Connect the host to a computer with USB debugging enabled and run once:

```bash
adb shell pm grant com.decoy android.permission.WRITE_SECURE_SETTINGS
```

Check it with:

```bash
adb shell dumpsys package com.decoy | findstr WRITE_SECURE_SETTINGS
```

On macOS/Linux, use `grep WRITE_SECURE_SETTINGS` instead of `findstr`.

This permission normally survives an in-place APK update signed with the same key, but uninstalling the app removes the grant.

Phone Master preserves other existing `icon_blacklist` entries; it only adds/removes the `bluetooth` slot.

### Enable automation

Open Phone Master and enable **Automatic Bluetooth tethering**.

With automation enabled, test:

1. Turn mobile data ON.
2. Bluetooth should be ON.
3. Bluetooth tethering should become ON.
4. Turn Bluetooth OFF while mobile data remains ON. Phone Master should restore it.
5. Turn Bluetooth tethering OFF while mobile data remains ON. Phone Master should restore it.
6. Turn mobile data OFF. Phone Master should stop forcing Bluetooth/tethering state.

## 3. Install Tethering on the receiver

Install Tethering v1.0.9 and open it once.

Its foreground monitor checks approximately every two seconds. Its notification changes when the receiver moves between Bluetooth off, Bluetooth on but not tethered, tethered without internet, and tethered with internet.

The receiver app uses both Android network information and common MediaTek/XOS Bluetooth-PAN interfaces such as `bnep*` to recognize the tethered connection.

## 4. Android background/persistence settings

Android manufacturers can kill background services even when an app requests normal boot persistence. Configure **both apps** as generously as your phone allows.

On XOS/Infinix, look for settings with names such as **App Launch**, **Auto-start**, **Background activity**, **Battery optimization**, **Power Marathon**, or **Battery Lab**. Menu names vary by XOS version.

For both Phone Master and Tethering:

- Allow automatic/secondary launch if the device provides those switches.
- Allow background activity.
- Exclude the app from battery optimization / battery saver restrictions where possible.
- Open the app once after installation.
- Put the app in Recents and use the **padlock/lock** control if your XOS version provides it. This reduces the chance that the manufacturer clears it from memory.
- Do not use **Force stop** as a persistence test. Android places a force-stopped package into a stopped state and may prevent normal broadcast-based restart until the user launches it again.

A normal reboot is the meaningful persistence test.

## 5. Reboot persistence

Both apps contain Android boot handling, but some XOS versions may still prevent a third-party app from starting itself reliably after reboot. Termux:Boot can be used as a fallback wake-up mechanism.

### Phone Master Termux:Boot fallback

Phone Master v1.0.14 contains a dedicated headless receiver. The Termux command is:

```bash
am broadcast -a com.decoy.START_AUTOMATION -n com.decoy/.TermuxBootReceiver
```

This receiver respects Phone Master's **Automatic Bluetooth tethering** switch. It does not turn automation on if the user disabled it.

### Tethering Termux:Boot fallback

Tethering v1.0.9 contains a headless `StartReceiver`. The command is:

```bash
am broadcast -a com.decoy.internetwatch.START -n com.decoy.internetwatch/.StartReceiver
```

### Example Termux:Boot script

Install Termux and Termux:Boot from a trusted source. Open **Termux:Boot once** after installation so Android can register it.

In Termux:

```bash
mkdir -p ~/.termux/boot
nano ~/.termux/boot/start-tethering-tools.sh
```

On a host device, use:

```sh
#!/data/data/com.termux/files/usr/bin/sh
sleep 15
am broadcast -a com.decoy.START_AUTOMATION -n com.decoy/.TermuxBootReceiver
```

On a receiver device, use:

```sh
#!/data/data/com.termux/files/usr/bin/sh
sleep 15
am broadcast -a com.decoy.internetwatch.START -n com.decoy.internetwatch/.StartReceiver
```

Then:

```bash
chmod +x ~/.termux/boot/start-tethering-tools.sh
```

Give Termux and Termux:Boot the same battery/background/autostart allowances described above.

## 6. Recommended first-time setup order

**Host**

1. Install Phone Master.
2. Open it.
3. Grant Modify system settings.
4. Optionally grant `WRITE_SECURE_SETTINGS` through ADB for Bluetooth-icon hiding.
5. Pair the receiver.
6. Manually prove Bluetooth tethering works.
7. Enable Automatic Bluetooth tethering.
8. Configure battery/autostart/Recents-lock settings.
9. Configure the Termux:Boot fallback if the device does not restart Phone Master reliably after reboot.
10. Reboot and test without manually opening Phone Master.

**Receiver**

1. Pair it with the host.
2. Manually prove **Internet access** over the paired host works.
3. Install and open Tethering once.
4. Configure battery/autostart/Recents-lock settings.
5. Configure Termux:Boot fallback if needed.
6. Reboot and confirm the monitor returns.

## 7. Daily use

Normally:

1. Turn mobile data on on the host.
2. Phone Master keeps host Bluetooth and Bluetooth tethering available.
3. The receiver's Tethering notification tells the user when to turn on Bluetooth/connect to the paired host.
4. Use the receiver's normal Android Bluetooth Internet/PAN control to connect.
5. When the PAN connection has internet, Tethering reports **Internet access**.

## 8. Troubleshooting

### Host does not enable tethering

Confirm **Modify system settings** is granted to Phone Master. The tested MediaTek/XOS tethering service rejects tethering changes without this special access.

Also confirm Phone Master's automation switch is on and mobile data is on.

### Bluetooth icon will not hide

Run the ADB `WRITE_SECURE_SETTINGS` grant again and verify it. This is unrelated to Modify system settings.

### Everything worked until reboot

Open both apps once, enable manufacturer autostart/background permissions, remove battery restrictions, lock them in Recents, and configure Termux:Boot.

### Receiver says Connect to Tethering

Bluetooth is on, but Android does not currently expose an active Bluetooth PAN connection. Open the paired host's Bluetooth entry on the receiver and enable Internet access/PAN.

### Receiver says No Internet connection

The Bluetooth PAN link exists but the receiver's connectivity probe cannot reach the internet. Check host mobile data, signal, carrier connectivity and Bluetooth tethering.

### Receiver never recognizes the link

This build was tuned for Android 9/XOS/MediaTek. Other manufacturers may expose Bluetooth PAN differently.

## 9. Building from source

The repository contains two independent Android Gradle projects:

```text
Host/       Phone Master v1.0.14
Receiver/   Tethering v1.0.9
```

Build each separately:

```bash
cd Host
gradle assembleDebug

cd ../Receiver
gradle assembleDebug
```

The GitHub Actions workflow builds both APKs and does **not** store a signing keystore in the public repository.

## 10. Security and privacy

Phone Master controls local Bluetooth/tethering state and optionally changes the local status-bar icon blacklist. Tethering checks local connection state and makes a small HTTP connectivity request to Google's Android connectivity-check endpoint to determine whether internet access is actually available.

Review the source before installing if you are using these utilities on a device containing sensitive information.

## Production versions

- Phone Master / Host: **v1.0.14**
- Tethering / Receiver: **v1.0.9**

These are the versions retained because they were the versions confirmed to work during device testing.
