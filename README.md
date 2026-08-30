# FilzaPDF

A free, open-source, local-first PDF reader built with Java, JavaFX, and Apache PDFBox.

FilzaPDF is being developed as a serious desktop PDF application focused on performance, usability, and user ownership of documents.

The long-term goal is to build a modern, lightweight alternative to bloated and subscription-heavy document software.

---

## Current Status

🚧 **Active Development — Pre-Release**

FilzaPDF currently has a functional PDF viewing experience with:

- PDF opening and rendering
- Multi-page document viewing
- Lazy background page rendering
- Page navigation
- Continuous scrolling
- Zoom controls
- Fit Width / Fit Page
- Page thumbnails
- PDF bookmarks
- PDF text search
- Recent files
- Per-document viewing state
- Dark/light themes
- Drag-and-drop PDF opening
- Fullscreen mode
- Annotation architecture
- Undo/redo foundation
- Automated testing

The application is **not yet a production-ready PDF replacement**.

Some advanced functionality is still being developed, particularly visual annotation tools, annotation persistence, and precise search-result highlighting.

---

## Why FilzaPDF?

Many modern productivity applications increasingly rely on subscriptions, cloud services, and proprietary ecosystems.

FilzaPDF follows a different philosophy:

> **Your documents should belong to you.**

The project aims to be:

- Free to use
- Open source
- Local-first
- Privacy-friendly
- Lightweight
- Offline-capable
- Based on open document formats where possible

There should be no requirement to upload a private PDF to a server simply to read, search, or annotate it.

---

## Features

### PDF Viewer

- Open PDF files
- Multi-page viewing
- Continuous vertical scrolling
- Current-page tracking
- Page number navigation
- Previous / next page
- Zoom in / out
- Reset zoom
- Fit Width
- Fit Page
- Keyboard navigation
- Centered page layout

### Performance

FilzaPDF does not render an entire document at full resolution when it is opened.

Instead:

```text
PDF
 │
 ├── Current page
 ├── Nearby pages
 │
 └── Other pages remain unloaded