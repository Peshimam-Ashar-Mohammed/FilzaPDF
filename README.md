# FilzaPDF

[![FilzaPDF CI](https://github.com/Peshimam-Ashar-Mohammed/FilzaPDF/actions/workflows/ci.yml/badge.svg)](https://github.com/Peshimam-Ashar-Mohammed/FilzaPDF/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-7C66DC.svg)](LICENSE)

**Free document tools. Your files. Your control.**

FilzaPDF is a Java-based desktop document productivity suite designed to bring PDF viewing, document editing, file conversion, annotations, and page management into one application.

Built with **Java 21, JavaFX 21, Apache PDFBox 3, Apache POI, and Gradle**, FilzaPDF aims to make powerful document tools accessible through a free, local-first desktop experience.

> **Our vision:** Useful document software should be accessible, transparent, and designed around the user's control of their files.

---

## Table of Contents

- [Features](#features)
- [Engineering Architecture (v0.2)](#engineering-architecture-v02)
- [Technology Stack](#technology-stack)
- [Privacy and Philosophy](#privacy-and-philosophy)
- [About FilzaPDF](#about-filzapdf)
- [The Bigger Vision](#the-bigger-vision)
- [Getting Started](#getting-started)
- [Build and Test](#build-and-test)
- [Project Status & Technical Scope](#project-status--technical-scope)
- [Contributing](#contributing)
- [About the Developer](#about-the-developer)
- [License](#license)

---

## Features

### 1. Crisp Progressive HiDPI PDF Viewing & Rendering

- **HiDPI Supersampled Rendering:** Renders pages at device-pixel-aware resolutions (`150–360 DPI`) with subpixel antialiasing, bicubic interpolation, and fractional font metrics so text and vector graphics remain sharp across zoom levels.
- **Two-Pass Progressive Viewport:** Immediately displays the best available cached resolution or a fast `96 DPI` preview while sharpening visible pages in the background.
- **Epoch-Guarded Cancellable Tasks:** Background render tasks are guarded by an `AtomicLong` render generation counter so rapid zooming or scrolling never allows stale renders to overwrite newer zoom levels.
- **Thread-Safe Memory-Budgeted Page Cache:** Synchronized LRU `PageCache` bounded by both entry count and a `192 MB` heap byte budget (`getUsedBytes()`).

### 2. PDF Text Recognition and Editing

Inspect recognized text blocks and their typography directly on a PDF page.

- **Text recognition:** Extract text blocks and their bounding boxes, with available font family, style, and size information.
- **Content-stream token scrubbing & replacement:** Inspect and modify supported text properties via the **Typography Inspector** (`Ctrl + E`). When replacing text, FilzaPDF parses the page's PDF content stream (`PDFStreamParser` + `ContentStreamWriter`) to scrub the original `Tj`/`TJ` string operands directly—avoiding opaque cover boxes over white/transparent backgrounds unless a custom background fill is explicitly chosen.
- **TrueType Unicode font fallback:** Automatically embeds system TrueType fonts (`PDType0Font`) when non-WinAnsi / Unicode characters are entered.
- **Insert text & place images:** Add new styled text blocks using `Ctrl + T` or place images and signatures directly onto PDF pages.

*Note: Editing existing PDF text can be constrained by the original document's font encoding, layout, and text structure.*

### 3. PDF Merger and Composer

Combine multiple PDF documents and customize the order of their pages using `Ctrl + M`.

- **Sequential merging:** Combine documents one after another.
- **Interleaved merging:** Alternate pages or page chunks from different documents.
- **Custom page recipes:** Define page sequences with expressions such as `A:1, B:1-2, A:2-end`.
- **Page selection & output queue:** Add specific page ranges, reorder selected pages, preview thumbnails, and open or save the resulting PDF.

### 4. Images to PDF Studio

Create PDFs from image collections.

- Import or drag and drop supported images: PNG, JPG, JPEG, BMP, and GIF.
- Reorder images before conversion.
- Select A4, Letter, or image-sized pages.
- Configure portrait or landscape orientation and page margins.
- Create a new PDF or append image pages to an existing document.

### 5. Word and Document Studio

Work with structured text documents and Microsoft Word-compatible files.

- Create documents containing headings, paragraphs, lists, dividers, page breaks, and images.
- Open and edit supported `.docx`, `.txt`, and `.md` files.
- Save documents as `.docx` or export directly to a paginated vector PDF.
- Convert PDF text content into an editable `.docx` document.

**Format compatibility note:** `WordDocumentService` maps `.docx` paragraphs, headings, lists, tables, and images into a structured block model (`DocumentBlock`). PDF-to-Word and Word-to-PDF workflows are designed as a practical document studio and converter rather than a full replica of Microsoft Word's complex floating layout engine.

### 6. PDF Annotations, Markup, and Content-Stream Redaction

Annotate documents using vector markup tools:

- Highlight, Underline, Strikethrough, Freehand ink, Rectangle markup, and Sticky notes
- **Content-Stream Redaction (`REDACT`):** Locates text blocks intersecting the redaction box, scrubs the underlying text operators (`Tj`, `TJ`, `'`, `"`) from the PDF page's content stream, and burns the black redaction bar over the region so redacted text cannot be extracted via `PDFTextStripper` after saving.
- Undo and redo with `Ctrl + Z` and `Ctrl + Y`
- Save annotations permanently to PDF (`Ctrl + S`)

### 7. Page Management & Document Security

Organize and protect PDF documents with page-level tools:

- Rotate pages, insert blank pages, delete pages, and extract selected page ranges.
- Export pages as PNG images (`150 DPI`).
- Add diagonal watermarks and stamp page numbers.
- Apply password protection and **256-bit AES encryption**.

---

## Engineering Architecture (v0.2)

```text
com.filzapdf
├── app/
│   └── FilzaPdfApplication.java              # Main JavaFX shell, command bar, and workspace orchestration
├── ui/
│   ├── PdfViewerController.java              # Progressive HiDPI viewport rendering & interaction modes
│   ├── WelcomeDashboardView.java             # Home Launchpad cards & recent documents view
│   ├── PageOperationsDialogs.java            # Focused dialogs for text/image insertion, extraction, watermarks & AES encryption
│   ├── PdfTextInspectorPane.java             # Right-hand inspector for in-place PDF text & font editing
│   ├── PdfMergerStudioDialog.java            # Custom page-by-page PDF merger & recipe composer
│   ├── ImageToPdfDialog.java                 # Multi-image to PDF builder & page inserter
│   ├── WordStudioDialog.java                 # Structured .docx / PDF document studio
│   ├── AboutFilzaDialog.java                 # Project story, vision, and developer links
│   └── SvgIcons.java                         # Crisp 16x16 vector SVGPath icons
├── renderer/
│   ├── PdfPageRenderer.java                  # Synchronized HiDPI PDFBox renderer (RenderDestination.VIEW, bicubic, no subsampling)
│   └── PageCache.java                        # Thread-safe LRU page cache with 192 MB byte memory budget
├── pdf/
│   ├── PdfTextEditorService.java             # Text/font recognition, content-stream token scrubbing & replacement, Unicode font embedding
│   ├── PdfToolboxService.java                # Custom merging, splitting, rotation, watermarks, page numbering & 256-bit AES encryption
│   ├── PdfCreatorService.java                # Image-to-PDF engine & structured block-to-PDF layout engine
│   └── WordDocumentService.java              # Apache POI .docx <-> DocumentBlock converter
└── annotations/
    ├── AnnotationManager.java                # Command-pattern undo/redo annotation state
    └── PdfAnnotationPersistenceService.java  # Content-stream redaction + vector annotation persistence
```

---

## Technology Stack

| Technology | Purpose |
|---|---|
| Java 21 | Core application logic |
| JavaFX 21 | Desktop user interface |
| Apache PDFBox 3 | PDF rendering, content-stream parsing, and manipulation |
| Apache POI | Microsoft Office (`.docx`) document processing |
| Gradle | Build automation and dependency management |
| JUnit 5 | Automated unit and integration testing |

---

## Privacy and Philosophy

FilzaPDF is designed around a local-first approach: document processing happens on your computer rather than requiring you to upload files to an online conversion service.

The project's guiding principles are:

- **Accessible:** Make useful document functionality available without mandatory subscriptions.
- **Local-first:** Keep document workflows under the user's control.
- **Open source:** Make the code available for inspection, learning, and community improvement.
- **Interoperable:** Support widely used document formats wherever practical.
- **User-focused:** Prioritize practical features, usability, reliability, and performance.

Local-first design does not automatically guarantee complete privacy or security. Review the implementation and dependencies, particularly before using the application with sensitive documents.

---

## About FilzaPDF

### Why I Built This

FilzaPDF began with a simple idea: **powerful document tools shouldn't have to come with expensive subscriptions, unnecessary complexity, or dependence on cloud services.**

People use PDFs and office documents every day to study, work, share information, and manage important records. Yet advanced editing and conversion capabilities can be expensive, and online tools may require uploading private files to third-party servers.

I wanted to explore another approach: a desktop application that brings useful document workflows together while giving users greater control over their files.

FilzaPDF is my attempt to turn that idea into working software.

Building it involves much more than adding buttons to a user interface. It means dealing with document formats, rendering, page layouts, typography, caching, application architecture, persistence, testing, and the edge cases that appear when real files meet software.

The goal is to keep improving the application into a practical tool that people can use, study, and contribute to.

### The Bigger Vision

FilzaPDF is part of a broader vision for **Filza**: an ecosystem of free and open-source productivity software.

The long-term ambition includes tools for:

- Documents and word processing
- Spreadsheets and data analysis
- Presentations
- PDF creation, conversion, and editing
- File management
- Document organization and synchronization

The aim is not simply to reproduce existing software, but to build useful alternatives that prioritize accessibility, interoperability, and user control.

This is a long-term ambition, not a claim that every application is already available. FilzaPDF is one step toward that vision.

### Built to Learn, Built to Share

I'm developing FilzaPDF while pursuing my B.Tech in Computer Science and Engineering.

This project is both a practical application and an opportunity to learn software engineering through real implementation challenges. It gives me room to explore Java, desktop application development, document processing, performance optimization, testing, and maintainable architecture.

I want the project to grow beyond a personal experiment into software that others can explore, test, improve, and potentially find useful.

If you discover a bug, have an idea, or want to contribute, feedback is welcome.

---

## Getting Started

### Prerequisites

Before building FilzaPDF, make sure you have:

- **Java Development Kit (JDK) 21**
- Git, if cloning the repository
- A supported desktop environment for JavaFX

Gradle is managed through the included Gradle Wrapper, so a separate Gradle installation should not be necessary.

### Clone the Repository

```bash
git clone https://github.com/Peshimam-Ashar-Mohammed/FilzaPDF.git
cd FilzaPDF
```

### Run the Application

**Windows — PowerShell:**

```powershell
.\gradlew.bat run
```

**Linux and macOS:**

```bash
chmod +x gradlew
./gradlew run
```

---

## Build and Test

Run the automated JUnit 5 test suite:

**Windows:**

```powershell
.\gradlew.bat test
```

**Linux and macOS:**

```bash
./gradlew test
```

Build the project:

```bash
./gradlew build
```

On Windows, use `.\gradlew.bat build`.

---

## Project Status & Technical Scope

FilzaPDF is an actively developed project. Features, compatibility, and behavior may change as development continues:

- **PDF Content-Stream Text Editing:** PDF is a fixed-layout page description format rather than a reflowable word processor format. `PdfTextEditorService` extracts positioned `TextPosition` runs, scrubs matching string operands from the page's content stream (`Tj`, `TJ`, `'`, `"`), and writes replacement text at the original baseline coordinates. This works well for single-line text blocks and redaction, but does not reflow multi-paragraph columns or edit glyph-positioned subsetted fonts in-place without substituting standard or system TrueType fonts.
- **Redaction Scope:** `REDACT` scrubs matching text tokens from the page content stream inside the redaction rectangle and draws a permanent black box over the region (verified by automated JUnit tests using `PDFTextStripper`). Embedded raster images or vector paths underneath a redaction box are visually covered by the burned rectangle; for scanned image-only PDFs, exporting/rasterizing the page is recommended for full pixel removal.
- **Before relying on FilzaPDF for critical documents**, test the workflows you need with representative files and keep backups of originals.

---

## Contributing

Contributions, bug reports, feature suggestions, and testing feedback are welcome.

A useful contribution might involve:

- Reproducing and reporting a bug
- Improving PDF rendering or editing behavior
- Expanding document-format compatibility
- Improving accessibility and interface usability
- Adding automated tests
- Improving documentation or performance

To contribute:

1. Fork the repository.
2. Create a branch for your changes.
3. Make your changes and run the relevant tests.
4. Open a pull request describing what changed and how it was tested.

Please include a minimal reproduction example when reporting a document-processing bug, and avoid sharing confidential documents or personal information.

---

## About the Developer

I'm **Ashar**, a Computer Science and Engineering student interested in Java, software development, Linux, open-source projects, and building practical tools that solve real problems.

**Connect with me:**

- **GitHub:** https://github.com/Peshimam-Ashar-Mohammed
- **Portfolio:** https://peshimam-ashar-mohammed.github.io/Portfolio/
- **FilzaPDF Repository:** https://github.com/Peshimam-Ashar-Mohammed/FilzaPDF

---

## License

Released under the [MIT License](LICENSE). See the [`LICENSE`](LICENSE) file for full terms.

---

<p align="center">

**FilzaPDF — Free document tools. Your files. Your control.**

*Built with curiosity. Improved through engineering. Open to possibilities.*

</p>
