# Security Risk Assessment - Fin App

This document provides a thorough security analysis of the Fin (finance) application codebase as of July 15, 2026.

## Executive Summary
The Fin app handles highly sensitive financial data by parsing incoming SMS messages from banks and financial institutions. While the app implements several good security practices (Biometric App Lock, Encrypted manual backups), there are significant risks related to **data at rest** and **cloud backups** that should be addressed to protect user privacy.

---

## 🚩 High Priority Risks

### 1. Unencrypted Local Database
The application stores all parsed transactions, account numbers (partial), merchant details, and asset holdings in a standard SQLite database (`fin.db`) using the Room persistence library.
- **Risk**: If a device is compromised (e.g., rooted or via a system vulnerability), an attacker could read the entire financial history of the user.
- **Location**: [AppDatabase.kt](file:///Users/neeraj/Documents/Apps/Fin/app/src/main/java/com/neeraj/fin/data/db/AppDatabase.kt)
- **Recommendation**: Use **SQLCipher** to encrypt the database at rest.

### 2. Sensitive Data in Cloud Backups
The app configuration explicitly includes the unencrypted database and settings in Android's Auto Backup and Cloud Backup.
- **Risk**: Sensitive financial data is uploaded to Google's cloud servers. While Google encrypts these backups, the app itself provides no additional layer of protection for this data.
- **Location**: [backup_rules.xml](file:///Users/neeraj/Documents/Apps/Fin/app/src/main/res/xml/backup_rules.xml) and [data_extraction_rules.xml](file:///Users/neeraj/Documents/Apps/Fin/app/src/main/res/xml/data_extraction_rules.xml)
- **Recommendation**: Exclude the database from cloud backups unless it is encrypted with a user-provided key, OR rely solely on the manual encrypted backup feature.

---

## ⚠️ Medium Priority Risks

### 3. Plaintext CSV Export
The app allows users to export their transaction history and holdings to CSV files.
- **Risk**: These files are written in plaintext to a user-selected location. Users may inadvertently store these files in insecure locations (unencrypted SD cards, public cloud folders) or share them over insecure channels.
- **Location**: [BackupManager.kt](file:///Users/neeraj/Documents/Apps/Fin/app/src/main/java/com/neeraj/fin/data/backup/BackupManager.kt#L143)
- **Recommendation**: Add a warning to the user before exporting to CSV about the sensitive nature of the data.

### 4. UI-Level App Lock (Bypassable)
The "App Lock" feature uses `BiometricPrompt` but only at the UI layer.
- **Risk**: Since the underlying database is unencrypted, the lock only prevents casual snooping. It does not protect the data from an attacker who can access the file system or use the Android Backup service to restore the app on another device.
- **Location**: [MainActivity.kt](file:///Users/neeraj/Documents/Apps/Fin/app/src/main/java/com/neeraj/fin/MainActivity.kt)
- **Recommendation**: Combine the App Lock with database encryption where the decryption key is derived from a biometric-protected secret in the Android Keystore.

---

## ℹ️ Low Priority & Inherent Risks

### 5. SMS Permissions (`READ_SMS`, `RECEIVE_SMS`)
The app requires high-privilege permissions to function.
- **Analysis**: This is a core requirement for the app's functionality (automatic expense tracking). However, the app must ensure that the `SmsReceiver` only processes relevant messages and does not leak message content to logs or other components.
- **Mitigation**: The current `SmsParser` uses heuristics to filter out non-transactional messages and personal senders.

### 6. Exported Components
The `SmsReceiver` and `KoshWidgetReceiver` are exported in the manifest.
- **Analysis**: `SmsReceiver` is correctly protected by the `android.permission.BROADCAST_SMS` permission, ensuring only the system can trigger it.
- **Location**: [AndroidManifest.xml](file:///Users/neeraj/Documents/Apps/Fin/app/src/main/AndroidManifest.xml)

---

## ✅ Security Best Practices Implemented
- **Encrypted Manual Backups**: Uses AES-256-GCM with PBKDF2-HMAC-SHA256 (200k iterations) for manual backups.
- **No Cleartext Traffic**: The app does not allow cleartext network traffic.
- **Biometric Integration**: Uses the official `androidx.biometric` library for user authentication.
- **Minimal Dependencies**: The app uses a small set of well-maintained Jetpack libraries, reducing the supply-chain attack surface.

## 🚀 Recommended Mitigation Plan
1.  **Integrate SQLCipher**: Implement full-disk encryption for the Room database.
2.  **Keystore Integration**: Use the Android Keystore System to store a master key for database encryption, potentially tied to biometric authentication.
3.  **Refine Backup Rules**: Remove `fin.db` from `cloud-backup` in `data_extraction_rules.xml` to prevent unencrypted financial data from leaving the device via the system backup service.
