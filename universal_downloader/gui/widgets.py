"""
widgets.py — Reusable custom widgets for the Universal Downloader GUI.

Components:
  - BrowseEntry    : Label + Entry + Browse button in one frame
  - ToggleButton   : ON/OFF toggle with color state
  - LogPanel       : Scrollable log viewer with color-coded lines
"""

import customtkinter as ctk


# ── Palette ────────────────────────────────────────────────────────────────────
COLORS = {
    "bg":        "#1a1a2e",
    "surface":   "#16213e",
    "panel":     "#0f3460",
    "accent":    "#e94560",
    "accent2":   "#533483",
    "text":      "#eaeaea",
    "subtext":   "#8892b0",
    "success":   "#57cc99",
    "warning":   "#f4a261",
    "error":     "#e63946",
    "download":  "#48cae4",
    "border":    "#2a2a4a",
}


# ── BrowseEntry ────────────────────────────────────────────────────────────────
class BrowseEntry(ctk.CTkFrame):
    """
    A single-row widget: [Label]  [Entry field]  [Browse button]
    Clicking Browse opens a file or directory picker.
    """

    def __init__(
        self,
        parent,
        label: str,
        mode: str = "file",   # "file" | "dir"
        placeholder: str = "",
        **kwargs,
    ):
        super().__init__(parent, fg_color="transparent", **kwargs)

        self._mode = mode

        self.columnconfigure(1, weight=1)

        # Label
        ctk.CTkLabel(
            self,
            text=label,
            width=130,
            anchor="w",
            text_color=COLORS["subtext"],
            font=("Segoe UI", 12),
        ).grid(row=0, column=0, padx=(0, 8), sticky="w")

        # Entry
        self._entry = ctk.CTkEntry(
            self,
            placeholder_text=placeholder,
            fg_color=COLORS["surface"],
            border_color=COLORS["border"],
            text_color=COLORS["text"],
            placeholder_text_color=COLORS["subtext"],
            font=("Segoe UI", 12),
            height=34,
        )
        self._entry.grid(row=0, column=1, sticky="ew", padx=(0, 8))

        # Browse button
        ctk.CTkButton(
            self,
            text="Browse",
            width=70,
            height=34,
            fg_color=COLORS["panel"],
            hover_color=COLORS["accent2"],
            text_color=COLORS["text"],
            font=("Segoe UI", 12),
            command=self._browse,
        ).grid(row=0, column=2)

    def _browse(self):
        from tkinter import filedialog
        if self._mode == "dir":
            path = filedialog.askdirectory(title="Select Download Folder")
        else:
            path = filedialog.askopenfilename(
                title="Select File",
                filetypes=[("Text files", "*.txt"), ("All files", "*.*")],
            )
        if path:
            self.set(path)

    def get(self) -> str:
        return self._entry.get().strip()

    def set(self, value: str):
        self._entry.delete(0, "end")
        self._entry.insert(0, value)


# ── ToggleButton ───────────────────────────────────────────────────────────────
class ToggleButton(ctk.CTkButton):
    """
    A button that visually toggles ON/OFF.
    ON  → accent color with ✓ prefix
    OFF → muted panel color with ✗ prefix
    """

    def __init__(
        self,
        parent,
        label: str,
        initial: bool = False,
        **kwargs,
    ):
        self._label = label
        self._state = initial

        super().__init__(
            parent,
            text=self._make_text(),
            fg_color=self._get_fg(),
            hover_color=self._get_hover(),
            text_color=COLORS["text"],
            font=("Segoe UI", 13, "bold"),
            height=40,
            corner_radius=8,
            command=self._toggle,
            **kwargs,
        )

    def _make_text(self) -> str:
        icon = "✓" if self._state else "✗"
        return f"{icon}  {self._label}"

    def _get_fg(self) -> str:
        return COLORS["accent"] if self._state else COLORS["panel"]

    def _get_hover(self) -> str:
        return "#c0392b" if self._state else COLORS["accent2"]

    def _toggle(self):
        self._state = not self._state
        self.configure(
            text=self._make_text(),
            fg_color=self._get_fg(),
            hover_color=self._get_hover(),
        )

    def get(self) -> bool:
        return self._state

    def set(self, value: bool):
        self._state = bool(value)
        self.configure(
            text=self._make_text(),
            fg_color=self._get_fg(),
            hover_color=self._get_hover(),
        )


# ── LogPanel ───────────────────────────────────────────────────────────────────
class LogPanel(ctk.CTkTextbox):
    """
    Scrollable, read-only log panel with color-coded tags:
      info      → dim white
      success   → green
      warn      → orange
      error     → red
      download  → cyan
    """

    TAG_COLORS = {
        "info":     COLORS["subtext"],
        "success":  COLORS["success"],
        "warn":     COLORS["warning"],
        "error":    COLORS["error"],
        "download": COLORS["download"],
    }

    def __init__(self, parent, **kwargs):
        super().__init__(
            parent,
            fg_color=COLORS["surface"],
            text_color=COLORS["text"],
            font=("Consolas", 11),
            state="disabled",
            wrap="word",
            **kwargs,
        )

        # Register color tags on the underlying tk Text widget
        for tag, color in self.TAG_COLORS.items():
            self._textbox.tag_configure(tag, foreground=color)

    def append(self, line: str, tag: str = "info"):
        """Append a line to the log panel (thread-safe via Tk's event loop)."""
        self.configure(state="normal")
        self._textbox.insert("end", line + "\n", tag)
        self._textbox.see("end")
        self.configure(state="disabled")

    def clear(self):
        self.configure(state="normal")
        self.delete("1.0", "end")
        self.configure(state="disabled")
