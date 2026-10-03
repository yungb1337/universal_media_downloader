# ADR-003: Jetpack Compose with Material 3 UI Architecture

## Status
Accepted

## Context
The application needs a modern, responsive, touch-friendly UI for Android supporting dark mode, dynamic system colors (Material You), real-time progress animations, batch editing, and log streams.

## Decision
Build 100% of the UI using Jetpack Compose with Material Design 3 and Unidirectional Data Flow (UDF).
- Single Activity architecture (`MainActivity`).
- State managed via Kotlin `StateFlow` and `DownloadViewModel`.
- Smooth animated progress bars (`animateFloatAsState`).

## Consequences
- **Positive**: Clean declarative UI code, zero XML layout bloat, seamless reactive state bindings.
- **Positive**: Native support for Android 12+ dynamic color theming.
