#!/usr/bin/env node

/**
 * SimpliX Documentation Validator
 *
 * Validates documentation files against project guidelines and templates.
 * Reference: .claude/references/documentation-guidelines.md
 *            .claude/references/documentation-templates.md
 *
 * This script uses only fs/promises for file operations.
 * No shell execution or child_process is used.
 */

import { readdir, readFile, access, stat } from 'fs/promises';
import { join, dirname, basename, relative } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const PROJECT_ROOT = join(__dirname, '../..');

// Symbols
const SYMBOLS = {
  success: '\u2714',
  error: '\u2716',
  warning: '\u26A0',
  info: '\u2139'
};

// Module definitions
const MODULES = [
  'simplix-core',
  'simplix-auth',
  'simplix-cache',
  'simplix-email',
  'simplix-encryption',
  'simplix-event',
  'simplix-excel',
  'simplix-file',
  'simplix-hibernate',
  'simplix-mybatis',
  'spring-boot-starter-simplix'
];

// Required files per module
const REQUIRED_FILES = {
  'README.md': true,
  'docs/ko/overview.md': true
};

// Required sections in README.md (Korean or English)
const README_REQUIRED_SECTIONS = [
  { patterns: ['# SimpliX'], description: 'Title' },
  { patterns: ['## Features', '## 주요 기능'], description: 'Features' },
  { patterns: ['## Quick Start', '## 빠른 시작'], description: 'Quick Start' },
  { patterns: ['## Configuration', '## 설정', '## 기본 설정'], description: 'Configuration' },
  { patterns: ['## License', '## 라이선스'], description: 'License' }
];

// Required sections in overview.md (Korean or English)
const OVERVIEW_REQUIRED_SECTIONS = [
  { patterns: ['## Architecture', '## 아키텍처'], description: 'Architecture' },
  { patterns: ['## Core Components', '## 핵심 컴포넌트', '## 주요 컴포넌트'], description: 'Core Components' },
  { patterns: ['## Configuration Properties', '## 설정 속성', '## 설정 프로퍼티'], description: 'Configuration Properties' }
];

// Approved symbols (these are NOT emojis for this project)
const APPROVED_SYMBOLS = [
  '\u2714', // ✔ (success)
  '\u2716', // ✖ (error)
  '\u26A0', // ⚠ (warning)
  '\u2139'  // ℹ (info)
];

// Emoji regex - matches common emoji ranges but we'll filter out approved symbols
const EMOJI_RANGES_REGEX = /[\u{1F300}-\u{1F9FF}]|[\u{1F600}-\u{1F64F}]|[\u{1F680}-\u{1F6FF}]|[\u{2600}-\u{26FF}]|[\u{2700}-\u{27BF}]/gu;

// Version pattern (hardcoded versions like 1.0.15, 3.5.7)
const HARDCODED_VERSION_REGEX = /(?:version|Version|VERSION)[\s:='"]*(\d+\.\d+\.\d+)/g;
const DEPENDENCY_VERSION_REGEX = /<version>(\d+\.\d+\.\d+)<\/version>/g;

class ValidationResult {
  constructor() {
    this.errors = [];
    this.warnings = [];
    this.passed = [];
  }

  addError(message, file = null, line = null) {
    this.errors.push({ message, file, line });
  }

  addWarning(message, file = null, line = null) {
    this.warnings.push({ message, file, line });
  }

  addPassed(message) {
    this.passed.push(message);
  }

  get hasErrors() {
    return this.errors.length > 0;
  }

  get hasWarnings() {
    return this.warnings.length > 0;
  }
}

async function fileExists(filePath) {
  try {
    await access(filePath);
    return true;
  } catch {
    return false;
  }
}

async function readFileContent(filePath) {
  try {
    return await readFile(filePath, 'utf-8');
  } catch {
    return null;
  }
}

function checkRequiredSections(content, sections, fileName) {
  const issues = [];
  for (const section of sections) {
    // Support both simple string and pattern object format
    if (typeof section === 'string') {
      if (!content.includes(section)) {
        issues.push(`Missing section: ${section}`);
      }
    } else if (section.patterns) {
      // Check if any of the patterns match
      const found = section.patterns.some(pattern => content.includes(pattern));
      if (!found) {
        issues.push(`Missing section: ${section.description} (${section.patterns.join(' or ')})`);
      }
    }
  }
  return issues;
}

function checkEmojis(content, fileName) {
  const issues = [];
  const lines = content.split('\n');

  lines.forEach((line, index) => {
    const matches = line.match(EMOJI_RANGES_REGEX);
    if (matches) {
      // Filter out approved symbols
      const forbiddenEmojis = matches.filter(emoji => !APPROVED_SYMBOLS.includes(emoji));
      if (forbiddenEmojis.length > 0) {
        issues.push({
          message: `Forbidden emoji found: ${forbiddenEmojis.join(', ')}`,
          line: index + 1
        });
      }
    }
  });

  return issues;
}

function checkHardcodedVersions(content, fileName) {
  const issues = [];
  const lines = content.split('\n');

  lines.forEach((line, index) => {
    // Skip CLAUDE.md internal references
    if (fileName === 'CLAUDE.md') return;

    const versionMatch = line.match(HARDCODED_VERSION_REGEX);
    const dependencyMatch = line.match(DEPENDENCY_VERSION_REGEX);

    if (versionMatch || dependencyMatch) {
      // Skip if it's a placeholder pattern
      if (line.includes('${version}') || line.includes('${')) return;

      issues.push({
        message: `Possible hardcoded version found`,
        line: index + 1,
        content: line.trim().substring(0, 80)
      });
    }
  });

  return issues;
}

function checkMermaidDiagrams(content, fileName) {
  // Only check overview.md files
  if (!fileName.includes('overview')) return [];

  const hasMermaid = content.includes('```mermaid');
  if (!hasMermaid) {
    return [{ message: 'Overview document should contain Mermaid diagrams' }];
  }

  return [];
}

async function validateModule(moduleName) {
  const result = new ValidationResult();
  const modulePath = join(PROJECT_ROOT, moduleName);

  // Check if module exists
  if (!await fileExists(modulePath)) {
    result.addWarning(`Module directory not found: ${moduleName}`);
    return result;
  }

  // Check required files
  for (const [file, required] of Object.entries(REQUIRED_FILES)) {
    const filePath = join(modulePath, file);
    const exists = await fileExists(filePath);

    if (exists) {
      result.addPassed(`${file} exists`);

      // Validate file content
      const content = await readFileContent(filePath);
      if (content) {
        // Check required sections
        const sections = file === 'README.md' ? README_REQUIRED_SECTIONS : OVERVIEW_REQUIRED_SECTIONS;
        const sectionIssues = checkRequiredSections(content, sections, file);
        sectionIssues.forEach(issue => result.addWarning(issue, file));

        // Check emojis
        const emojiIssues = checkEmojis(content, file);
        emojiIssues.forEach(issue => result.addError(issue.message, file, issue.line));

        // Check hardcoded versions
        const versionIssues = checkHardcodedVersions(content, file);
        versionIssues.forEach(issue => result.addWarning(`${issue.message}: ${issue.content}`, file, issue.line));

        // Check Mermaid diagrams
        const mermaidIssues = checkMermaidDiagrams(content, file);
        mermaidIssues.forEach(issue => result.addWarning(issue.message, file));
      }
    } else if (required) {
      result.addWarning(`Missing required file: ${file}`, file);
    }
  }

  return result;
}

async function validateDocsFolder() {
  const result = new ValidationResult();
  const docsPath = join(PROJECT_ROOT, 'docs/ko');

  if (!await fileExists(docsPath)) {
    result.addWarning('docs/ko folder not found');
    return result;
  }

  // Check key documentation files
  const keyFiles = [
    '_sidebar.md',
    'README.md',
    'quick-start.md'
  ];

  for (const file of keyFiles) {
    const filePath = join(docsPath, file);
    if (await fileExists(filePath)) {
      result.addPassed(`docs/ko/${file} exists`);
    } else {
      result.addWarning(`Missing docs/ko/${file}`);
    }
  }

  return result;
}

function printResults(moduleName, result) {
  console.log(`\nValidating ${moduleName}...`);

  result.passed.forEach(msg => {
    console.log(`  ${SYMBOLS.success} ${msg}`);
  });

  result.warnings.forEach(({ message, file, line }) => {
    const location = file ? (line ? `${file}:${line}` : file) : '';
    console.log(`  ${SYMBOLS.warning} Warning: ${message}${location ? ` (${location})` : ''}`);
  });

  result.errors.forEach(({ message, file, line }) => {
    const location = file ? (line ? `${file}:${line}` : file) : '';
    console.log(`  ${SYMBOLS.error} Error: ${message}${location ? ` (${location})` : ''}`);
  });
}

async function main() {
  const args = process.argv.slice(2);
  const strictMode = args.includes('--strict');
  const targetModule = args.find(arg => !arg.startsWith('--'));

  console.log(`${SYMBOLS.info} SimpliX Documentation Validator\n`);
  console.log(`Mode: ${strictMode ? 'Strict (warnings are errors)' : 'Normal'}`);
  console.log('-'.repeat(50));

  let totalErrors = 0;
  let totalWarnings = 0;
  let totalPassed = 0;
  let modulesValidated = 0;

  // Filter modules if specific module requested
  const modulesToValidate = targetModule
    ? MODULES.filter(m => m.includes(targetModule))
    : MODULES;

  if (targetModule && modulesToValidate.length === 0) {
    console.log(`${SYMBOLS.error} No modules found matching: ${targetModule}`);
    process.exit(1);
  }

  // Validate each module
  for (const moduleName of modulesToValidate) {
    const result = await validateModule(moduleName);
    printResults(moduleName, result);

    totalErrors += result.errors.length;
    totalWarnings += result.warnings.length;
    totalPassed += result.passed.length;
    modulesValidated++;
  }

  // Validate docs folder
  const docsResult = await validateDocsFolder();
  printResults('docs/ko', docsResult);
  totalErrors += docsResult.errors.length;
  totalWarnings += docsResult.warnings.length;
  totalPassed += docsResult.passed.length;

  // Print summary
  console.log(`\n${'-'.repeat(50)}`);
  console.log(`${SYMBOLS.info} Summary`);
  console.log(`  Modules validated: ${modulesValidated}`);
  console.log(`  ${SYMBOLS.success} Passed: ${totalPassed}`);
  console.log(`  ${SYMBOLS.warning} Warnings: ${totalWarnings}`);
  console.log(`  ${SYMBOLS.error} Errors: ${totalErrors}`);

  // Exit code based on mode
  if (totalErrors > 0 || (strictMode && totalWarnings > 0)) {
    console.log(`\n${SYMBOLS.error} Validation failed`);
    process.exit(1);
  } else if (totalWarnings > 0) {
    console.log(`\n${SYMBOLS.warning} Validation passed with warnings`);
    process.exit(0);
  } else {
    console.log(`\n${SYMBOLS.success} All validations passed`);
    process.exit(0);
  }
}

main().catch(err => {
  console.error(`${SYMBOLS.error} Unexpected error:`, err.message);
  process.exit(1);
});