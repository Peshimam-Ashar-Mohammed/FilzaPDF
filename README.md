# FilzaPDF

**Free document tools. Your files. Your control.**

FilzaPDF is a Java-based desktop document productivity suite designed to bring PDF viewing, document editing, file conversion, annotations, and page management into one application.

Built with **Java 21, JavaFX 21, Apache PDFBox 3, Apache POI, and Gradle**, FilzaPDF aims to make powerful document tools accessible through a free, local-first desktop experience.

> **Our vision:** Useful document software should be accessible, transparent, and designed around the user's control of their files.

---

## Table of Contents

- [Features](#features)
- [Technology Stack](#technology-stack)
- [Privacy and Philosophy](#privacy-and-philosophy)
- [About FilzaPDF](#about-filzapdf)
- [The Bigger Vision](#the-bigger-vision)
- [Getting Started](#getting-started)
- [Build and Test](#build-and-test)
- [Project Status](#project-status)
- [Contributing](#contributing)
- [About the Developer](#about-the-developer)

---

## Features

### 1. PDF Text Recognition and Editing

Inspect recognized text blocks and their typography directly on a PDF page.

- **Text recognition:** Extract text blocks and their bounding boxes, with available font family, style, and size information.
- **Typography inspector:** Inspect and modify supported text properties, including font family, size, bold/italic styling, and colors.
- **Insert text:** Add new styled text blocks using `Ctrl + T`.
- **Image and signature placement:** Place images directly onto PDF pages.
- **Editing workflow:** Use `Ctrl + E` to access the PDF editing mode.

*Note: Editing existing PDF text can be constrained by the original document's font encoding, layout, and text structure.*

### 2. PDF Merger and Composer

Combine multiple PDF documents and customize the order of their pages using `Ctrl + M`.

- **Sequential merging:** Combine documents one after another.
- **Interleaved merging:** Alternate pages or page chunks from different documents.
- **Custom page recipes:** Define page sequences with expressions such as `A:1, B:1-2, A:2-end`.
- **Page selection:** Add specific page ranges to the output.
- **Output queue:** Reorder selected pages, preview thumbnails, and open or save the resulting PDF.

### 3. Images to PDF Studio

Create PDFs from image collections.

- Import or drag and drop supported images: PNG, JPG, JPEG, BMP, and GIF.
- Reorder images before conversion.
- Select A4, Letter, or image-sized pages.
- Configure portrait or landscape orientation and page margins.
- Create a new PDF or append image pages to an existing document.

### 4. Word and Document Studio

Work with text documents and Microsoft Word-compatible files.

- Create documents containing headings, paragraphs, lists, dividers, page breaks, and images.
- Open and edit supported `.docx`, `.txt`, and `.md` files.
- Save documents as `.docx`.
- Export documents to PDF.
- Convert PDF content into an editable Word document.

**Format compatibility note:** PDF-to-Word conversion may require layout adjustments. PDF files preserve positioned content rather than the semantic structure of a typical Word document, so conversion fidelity can vary.

### 5. PDF Annotations and Markup

Annotate documents using available markup tools.

- Highlight
- Underline
- Strikethrough
- Freehand drawing
- Rectangle markup
- Sticky notes
- Redaction tools
- Undo and redo with `Ctrl + Z` and `Ctrl + Y`
- Save supported annotations to PDF

### 6. Page Management

Organize PDF documents with page-level tools.

- Rotate pages.
- Insert blank pages.
- Delete pages.
- Extract selected page ranges.
- Export pages as PNG images.
- Add watermarks.
- Insert page numbers.

### 7. Document Security

Apply password protection and encryption using supported PDF security features, including AES-256 encryption where configured.

**Security note:** Password protection and encryption are different from secure redaction. If a document contains confidential information, verify that redaction permanently removes the underlying text and content before sharing it.

---

## Technology Stack

| Technology | Purpose |
|---|---|
| Java 21 | Core application logic |
| JavaFX 21 | Desktop user interface |
| Apache PDFBox 3 | PDF rendering and manipulation |
| Apache POI | Microsoft Office document processing |
| Gradle | Build automation and dependency management |
| JUnit 5 | Automated testing |

---

## Privacy and Philosophy

FilzaPDF is designed around a local-first approach: document processing should happen on your computer wherever practical, rather than requiring you to upload files to an online conversion service.

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

Run the automated test suite:

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

## Project Status

FilzaPDF is an actively developed project. Features, compatibility, and behavior may change as development continues.

PDFs vary significantly in their internal structure, fonts, encodings, and layouts. Word conversion and in-place PDF editing can therefore have limitations even when the relevant tools are available.

Before relying on FilzaPDF for important documents, test the workflows you need with representative files and keep backups of originals.

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

I'm a Computer Science and Engineering student interested in Java, software development, Linux, open-source projects, and building practical tools that solve real problems.

**Connect with me:**

- **GitHub:** https://github.com/Peshimam-Ashar-Mohammed
- **Portfolio:** https://peshimam-ashar-mohammed.github.io/Portfolio/
- **FilzaPDF Repository:** https://github.com/Peshimam-Ashar-Mohammed/FilzaPDF

---

## License

Please see the repository's `LICENSE` file for the terms under which FilzaPDF can be used, modified, and distributed.

If the project does not yet have a license, one should be selected and added before describing the software as formally open source. Making a repository public alone does not grant others permission to redistribute or modify its code.

---

<p align="center">

**FilzaPDF — Free document tools. Your files. Your control.**

*Built with curiosity. Improved through engineering. Open to possibilities.*

</p>
