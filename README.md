# MDE Mobile Terminal
Android-first hybrid terminal project.

## Build APK
GitHub Actions builds a debug APK on every push to main or when manually dispatched.
Open Actions -> Build MDE Mobile Terminal APK -> Run workflow. Download the artifact named MDE-Mobile-Terminal-debug-apk from a successful run.

## Execution modes
- CMD compatibility: a local subset of Windows-style commands, not cmd.exe.
- PowerShell compatibility: local command mapping, not a full PowerShell runtime.
- Android shell: executes commands using Android /system/bin/sh.
- Remote Windows: genuine PowerShell commands over SSH to a Windows PC with OpenSSH Server enabled.

Remote SSH host key checking must be provisioned before connecting. This starter keeps credentials in memory only. Do not expose SSH directly to the public internet.
