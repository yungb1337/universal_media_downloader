# ADR-002: Native QuickJS Engine for JavaScript Challenge Solving

## Status
Accepted

## Context
Recent YouTube extractor updates require a JavaScript runtime to solve `n` challenge signatures and proof-of-origin (PO) tokens. Standard Android environments do not bundle Node.js or Deno.

## Decision
Embed a lightweight native QuickJS C library compiled for Android ABIs (`arm64-v8a`, `armeabi-v7a`, `x86_64`) via JNI, and expose its binary executable path to `yt-dlp`'s `js_runtimes` configuration:
```python
opts = {
    "js_runtimes": {"quickjs": {"path": quickjs_bin_path}},
    "remote_components": ["ejs:github"]
}
```

## Consequences
- **Positive**: Complete offline JS signature challenge evaluation without relying on heavy WebViews or external node binaries.
- **Positive**: Minimal binary overhead (~1.5 MB total across ABIs).
- **Negative**: Requires multi-ABI native compilation in CMake/NDK.
