Generated Android runtime metadata lives here during APK packaging.

Expected generated asset names:
- runtime/BUILD_INFO.txt

Bundled executables and shared libraries are packaged via generated
`jniLibs/arm64-v8a` instead:
- jniLibs/arm64-v8a/libruclaw_exec.so
- jniLibs/arm64-v8a/libruclaw_launcher_exec.so
- jniLibs/arm64-v8a/libllama_server_exec.so
- jniLibs/arm64-v8a/*.so (runtime dependencies)

The Android app uses `applicationInfo.nativeLibraryDir` for executable files
to avoid Android `exec` restrictions on writable app directories.
