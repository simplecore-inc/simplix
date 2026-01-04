---
description: Validate SimpliX documentation for completeness, formatting, and template compliance
examples:
  - /validate-docs
  - /validate-docs simplix-auth
  - /validate-docs --strict
---

# Validate Documentation

Run comprehensive documentation validation for the SimpliX project.

## Validation Checks

Execute the validation script and report results:

```bash
node ${CLAUDE_PROJECT_DIR}/.claude/scripts/validate-docs.mjs
```

## What This Command Validates

1. **Required Files**
   - README.md exists in each module
   - docs/ko/overview.md exists in each module

2. **Template Compliance**
   - Required sections present (Features, Quick Start, Configuration, etc.)
   - Proper heading hierarchy
   - Configuration tables have correct columns

3. **Content Rules**
   - No hardcoded version numbers (use ${version} placeholder)
   - Only approved symbols (✔ ✖ ⚠ ℹ), no emojis
   - Mermaid diagrams present in overview documents
   - ASCII-only directory structure diagrams

4. **Link Validation**
   - Internal links point to existing files
   - Anchor references are valid

## Arguments

- Module name (optional): Validate specific module only (e.g., `simplix-auth`)
- `--strict`: Treat warnings as errors

## Output Format

```
ℹ SimpliX Documentation Validator

Validating simplix-core...
✔ README.md exists
✔ docs/ko/overview.md exists
✔ All required sections present

Validating simplix-auth...
⚠ Warning: Missing docs/ko/overview.md

--- Summary ---
Modules: 10, Passed: 9, Warnings: 1, Errors: 0
```

## Related References

- Documentation Guidelines: `.claude/references/documentation-guidelines.md`
- Documentation Templates: `.claude/references/documentation-templates.md`
