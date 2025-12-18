# File Integrity Monitoring Tool (FIM Java Project)

## Overview
This is a **Java-based File Integrity Monitoring (FIM) system** that monitors a directory in real-time and tracks file changes using **SHA-256 hashing**. It integrates with **MongoDB** to store file hashes and log events, making it suitable for security auditing and monitoring critical files.

---

## Features
- Monitors file creation, modification, and deletion in a specified folder (`watched_files` by default).
- Computes **SHA-256 hash** for file integrity verification.
- Stores **file hashes and events** in MongoDB.
- Sends logs to the system logger for auditing.
- Cross-platform (works on macOS, Linux, Windows with Java and MongoDB installed).

---

Test case:



![Untitled Project](https://github.com/user-attachments/assets/4fffee30-c828-4b8c-acb2-9ebd1827b6b3)
