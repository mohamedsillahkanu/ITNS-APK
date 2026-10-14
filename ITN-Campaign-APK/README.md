# ITN Mass Campaign app (Android APK)

The campaign app (all role cards, Conveyor DMS → PHU logistics, Warehouse Manager) as an
Android app. GitHub builds the APK for you; nothing needs installing on your computer.

In the app:
- the welcome video plays **with sound as soon as the app opens** (no tap);
- the camera works for scanning bale QR codes and for the waybill photo;
- receipts that are "printed" open Android's print screen (print, or save as PDF);
- it fits any phone screen and the phone's large-font setting does not break the layout;
- it opens without internet (everything is inside the app).

```
.github/workflows/build-apk.yml   GitHub builds the APK on every upload
app/src/main/assets/index.html    the campaign app itself (replace this file to update)
app/src/main/java/icf/itncampaign/MainActivity.java
app/itn-campaign.keystore         fixed signing key: new versions install over old ones
```

## Build the APK (about 10 minutes the first time)

1. On github.com: **New repository** → name it `itn-campaign-app` → **Create repository**.
2. Click **uploading an existing file** and drag in **everything inside this folder**,
   including the hidden `.github` folder. (Mac: Cmd+Shift+. shows hidden folders;
   Windows: File Explorer → View → Hidden items.)
3. **Commit changes**.
4. Open the **Actions** tab. "Build ITN Campaign APK" starts by itself (about 4 minutes).
   A green tick means it worked. A red cross: open it, copy the red lines and send them over.
5. Open **Releases** (right-hand side of the repository page). The newest release has
   **ITN-Campaign.apk**. Share that link with the field teams.

## Install on a phone

1. Open the Releases link on the phone and download **ITN-Campaign.apk**.
2. Open it. Android asks to allow installs from this source: **Allow**, then **Install**.
   (Play Protect may say the app is unknown: **Install anyway**.)
3. Open **ITN Campaign**. Allow the **camera** when asked (for scanning and waybill photos).

## Update the app later

Replace `app/src/main/assets/index.html` on GitHub with the new page (keep the name
`index.html`), commit, wait for the green tick, and install the new APK from Releases over
the old one. Anything saved on the phone is kept.

## Limits

- The APK is signed for internal use; it installs on any Android phone but is not set up for the Play Store.
- Android only. iPhones use the web version (GitHub Pages, add to home screen).
