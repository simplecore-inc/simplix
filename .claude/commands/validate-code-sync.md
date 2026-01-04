---
name: validate-code-sync
description: Validate that documentation references match actual code (classes, methods, properties)
---

# Code-Documentation Sync Validation

Validates that documentation accurately references actual code:

- **Classes**: Checks if class names mentioned in docs exist in source
- **Methods**: Verifies method signatures are present
- **Properties**: Confirms YAML configuration examples match Properties classes

## Usage

```bash
# Validate specific module
node .claude/scripts/validate-code-sync.mjs simplix-auth

# Validate all modules
node .claude/scripts/validate-code-sync.mjs --all

# Strict mode (warnings are errors)
node .claude/scripts/validate-code-sync.mjs simplix-auth --strict
```

## What It Checks

| Check | Description |
|-------|-------------|
| Class references | SimpliX* classes, *Service, *Provider patterns |
| Method signatures | Public methods in code blocks |
| YAML properties | simplix.* property paths |

## Filtering

The validator automatically ignores:
- Example/sample classes (Custom*, My*, Sample*, Example*)
- External framework classes (Spring Security, ShedLock)
- Variable names (lowercase start)
- Common Java types (String, List, etc.)

## Example Output

```
ℹ Validating simplix-auth...
  ✔ 274 references verified
  ⚠ Warning: Class 'AuthService' not found (may be user implementation)
  ⚠ Warning: Property 'simplix.encryption' (belongs to simplix-encryption)

Summary:
  ✔ Verified: 274
  ⚠ Warnings: 42
```

## Integration

Run after documentation changes or code refactoring to ensure docs stay in sync with code.