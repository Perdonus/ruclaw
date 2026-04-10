Place bundled Android local-runtime binaries here during APK packaging.

Expected asset names:
- runtime/ruclaw
- runtime/ruclaw-launcher

Optional asset name:
- runtime/llama-server

The Android app extracts these files into app-private storage when the user
presses the local "Установить" button in settings. `llama-server` is only
needed for the optional GGUF local-model path.
