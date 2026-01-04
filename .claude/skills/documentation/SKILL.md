---
name: documentation
description: Write and validate SimpliX documentation following project templates and guidelines. Use when creating README files, overview documents, feature guides, or any markdown documentation for SimpliX modules.
allowed-tools: Read, Write, Edit, Glob, Grep
---

# SimpliX Documentation Writing Skill

This skill provides guidance for writing documentation that follows SimpliX project standards.

## Reference Documents

Before writing any documentation, always read:
- `.claude/references/documentation-guidelines.md` - Complete formatting and structure rules
- `.claude/references/documentation-templates.md` - Mandatory templates for each document type

## Document Types and Templates

| Document Type | Location | Template |
|--------------|----------|----------|
| Module README | `{module}/README.md` | README.md Template |
| Module Overview | `{module}/docs/ko/overview.md` | Overview Document Template |
| Feature Guide | `{module}/docs/ko/{feature}-guide.md` | Feature Guide Template |
| Configuration Reference | `{module}/docs/ko/configuration.md` | Configuration Reference Template |
| API Documentation | `{module}/docs/ko/api-reference.md` | API Documentation Template |

## Critical Rules

### Language
- Korean documentation: Full Korean content allowed
- Code examples: Always use English identifiers and comments
- Technical terms may remain in English (Spring Boot, JPA, etc.)

### Symbols (NEVER use emojis)
- Use ONLY these symbols for status indicators:
  - Success/OK: (Unicode U+2714)
  - Error/Failed: (Unicode U+2716)
  - Warning: (Unicode U+26A0)
  - Info: (Unicode U+2139)

### Version Numbers
- NEVER hardcode version numbers
- Use `${version}` placeholder or omit entirely
- Bad: `<version>1.0.15</version>`
- Good: `implementation 'dev.simplecore:simplix-core'`

### Diagrams
- Use Mermaid for all diagrams except directory structures
- Korean docs use Korean labels in Mermaid
- Directory structures use ASCII only (English text)

## Section Header Language

- **Korean documentation uses Korean headers** (preferred for this project)
  - `## 주요 기능` (not `## Features`)
  - `## 빠른 시작` (not `## Quick Start`)
  - `## 설정` (not `## Configuration`)
  - `## 라이선스` (not `## License`)
  - `## 아키텍처` (not `## Architecture`)

## Required Sections by Document Type

### README.md
1. Title with module name (`# SimpliX {Module Name}`)
2. Features list with checkmarks (`## 주요 기능`)
3. Quick Start: dependency, configuration, usage (`## 빠른 시작`)
4. Configuration Summary table (`## 설정 요약`)
5. Architecture ASCII diagram (`## 아키텍처`)
6. Documentation links (`## 문서`)
7. License (`## 라이선스`)

### Overview Document
1. Architecture (Mermaid diagram)
2. Core Components
3. Auto-Configuration
4. Configuration Properties (YAML + table)
5. Sequence Diagram (Mermaid)
6. Related Documents

### Feature Guide
1. Introduction
2. Table of Contents
3. Concept comparison (if applicable)
4. Process flow (Mermaid)
5. REST API examples
6. Programmatic usage
7. Configuration
8. Troubleshooting
9. Related Documents

## Validation

After writing documentation, run validation:
```bash
node .claude/scripts/validate-docs.mjs
```

Or use the `/validate-docs` command.