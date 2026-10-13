# FilzaPDF

**Free document tools. Your files. Your control.**

A local-first, open-source PDF and Word document productivity suite built with **Java 21**, **JavaFX 21**, **Apache PDFBox 3**, and **Apache POI**.

---

## Core Capabilities

### 1. In-Place PDF Text & Font Recognition and Editing
- **Automatic Text & Font Recognition**: Extracts text blocks on any PDF page with their exact bounding box, font family (`Helvetica`, `Times`, `Courier`), bold/italic style, and font size in points.
- **Direct On-Page Editing**: Switch to **Edit PDF** (`Ctrl+E`), hover over any recognized text block on the page to inspect its font, and click to modify the text, font family, size, style, or colors in-place via the right-hand **Typography Inspector**.
- **Insert Text & Place Images**: Insert new styled text blocks (`Ctrl+T`) or embed images and signatures directly onto any page.

### 2. Custom Page-by-Page PDF Merger & Composer (`Ctrl+M`)
- Combine two or more PDFs in any custom page order:
  - **Sequential**: All pages from Doc A, followed by all pages from Doc B.
  - **Alternating / Interleaved Chunks**: Take *N* pages from Doc A, then *M* pages from Doc B (e.g., 1 page from A, 2 pages from B, repeated automatically).
  - **Custom Recipe Strings**: Use expressions like `A:1, B:1-2, A:2-end` to assemble exact page slices.
  - **Interactive Output Queue**: Add specific ranges, reorder pages, preview live thumbnails, and open or save the merged PDF.

### 3. Images to PDF Studio
- Upload or drag-and-drop multiple images (`PNG`, `JPG`, `JPEG`, `BMP`, `GIF`).
- Configure page size (`A4`, `LETTER`, or `FIT_IMAGE`), orientation (`Portrait` / `Landscape`), and page margins.
- Create a new multi-page PDF or append the image pages directly into the currently open PDF.

### 4. Word & Document Studio (`.docx` ↔ `.pdf`)
- **Rich Document Authoring**: Compose documents from scratch with headings, paragraphs, bullet lists, dividers, page breaks, and embedded images.
- **Microsoft Word (`.docx`) Editing**: Open existing `.docx` files, edit sections and typography, save back to `.docx`, or export directly to a paginated vector PDF.
- **PDF to Word Export**: Export any open PDF document to an editable `.docx` file.

### 5. Vector Markup, Organization & Security
- **Markup**: Highlight, Underline, Strikethrough, Freehand Ink, Rectangle, Redact, and Sticky Notes with full Undo/Redo (`Ctrl+Z` / `Ctrl+Y`) and permanent PDF persistence on Save (`Ctrl+S`).
- **Page Organization & Security**: Rotate pages, insert blank pages, delete pages, extract page ranges, add diagonal watermarks, stamp page numbers, export pages as PNG images, and apply **256-bit AES encryption**.

---

## About FilzaPDF

### Why I Built This

FilzaPDF started with a simple idea: **powerful document tools shouldn't have to come with expensive subscriptions, unnecessary complexity, or dependence on cloud services.**

Many people rely on PDF editors and office software for everyday tasks such as reading documents, editing text, merging files, adding annotations, and converting documents. Yet some advanced capabilities are locked behind paid plans, while online tools may require users to upload private files to third-party servers.

I wanted to explore a different approach.

**FilzaPDF is my attempt to build a free, local-first document productivity suite that puts users and their files first.**

Built with Java, JavaFX, and Apache PDFBox, the project brings PDF viewing, document manipulation, annotations, image-to-PDF creation, and Word document workflows into one desktop application. My goal is to keep developing it into a practical, accessible alternative for people who want useful tools without mandatory subscriptions or unnecessary cloud dependency.

### The Bigger Vision

FilzaPDF is part of a broader vision for **Filza**: an ecosystem of free and open-source productivity software.

The long-term ambition includes tools for documents, spreadsheets, presentations, file management, and document synchronization. Rather than building software around subscription lock-in, the goal is to create useful applications that people can run, inspect, modify, and improve.

This is a long-term vision, and FilzaPDF is one step toward it.

### Built to Learn, Built to Share

I'm building FilzaPDF as an independent software project while pursuing my B.Tech in Computer Science and Engineering.

Beyond the application itself, this project is an opportunity to explore real software engineering: PDF rendering, JavaFX UI architecture, background processing, caching, document formats, persistence, testing, and performance optimization.

I want this project to be useful to others, open to community contributions, and a foundation for learning through real-world development.

If you find the project interesting, have suggestions, or want to contribute, you're welcome to explore the repository and share your feedback.

### About the Developer

I'm Ashar, a Computer Science and Engineering student interested in software development, Java, open-source software, Linux, and building practical tools that solve real problems.

- **GitHub:** https://github.com/Peshimam-Ashar-Mohammed
- **Portfolio:** https://peshimam-ashar-mohammed.github.io/Portfolio/
- **FilzaPDF Repository:** https://github.com/Peshimam-Ashar-Mohammed/FilzaPDF

### Our Philosophy

- **Free to use:** Essential functionality should be accessible to everyone.
- **Local-first:** Your documents should remain under your control.
- **Open source:** Software should be transparent and open to improvement.
- **Interoperable:** Support common document formats wherever practical.
- **Community-driven:** Good software gets better when people can contribute.

The ambition is straightforward: build useful alternatives, learn by creating them, and give people more control over the software they use.

**FilzaPDF — Free document tools. Your files. Your control.**

---

## Build & Run

```bash
# Launch the desktop application
./gradlew run

# Run the automated JUnit 5 test suite
./gradlew test
```