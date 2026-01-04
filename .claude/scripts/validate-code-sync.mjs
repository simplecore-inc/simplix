#!/usr/bin/env node

/**
 * SimpliX Code-Documentation Sync Validator
 *
 * Validates that documentation references match actual code:
 * - Class names mentioned in docs exist in source
 * - Configuration properties in YAML examples exist in Properties classes
 * - Method signatures mentioned in docs exist in source
 *
 * Usage:
 *   node validate-code-sync.mjs [module-name]
 *   node validate-code-sync.mjs simplix-auth
 *   node validate-code-sync.mjs --all
 */

import { readdir, readFile, access } from 'fs/promises';
import { join, dirname, relative } from 'path';
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

class ValidationResult {
  constructor() {
    this.errors = [];
    this.warnings = [];
    this.passed = [];
    this.skipped = [];
  }

  addError(message, file = null, detail = null) {
    this.errors.push({ message, file, detail });
  }

  addWarning(message, file = null, detail = null) {
    this.warnings.push({ message, file, detail });
  }

  addPassed(message) {
    this.passed.push(message);
  }

  addSkipped(message) {
    this.skipped.push(message);
  }

  get hasErrors() {
    return this.errors.length > 0;
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

async function findJavaFiles(dir) {
  const files = [];
  try {
    const entries = await readdir(dir, { withFileTypes: true });
    for (const entry of entries) {
      const fullPath = join(dir, entry.name);
      if (entry.isDirectory()) {
        files.push(...await findJavaFiles(fullPath));
      } else if (entry.name.endsWith('.java')) {
        files.push(fullPath);
      }
    }
  } catch {
    // Directory doesn't exist
  }
  return files;
}

async function findMarkdownFiles(dir) {
  const files = [];
  try {
    const entries = await readdir(dir, { withFileTypes: true });
    for (const entry of entries) {
      const fullPath = join(dir, entry.name);
      if (entry.isDirectory()) {
        files.push(...await findMarkdownFiles(fullPath));
      } else if (entry.name.endsWith('.md')) {
        files.push(fullPath);
      }
    }
  } catch {
    // Directory doesn't exist
  }
  return files;
}

/**
 * Extract class names from Java source files
 */
function extractJavaClasses(content) {
  const classes = new Set();
  const classRegex = /(?:public\s+)?(?:abstract\s+)?(?:class|interface|enum)\s+(\w+)/g;
  let match;
  while ((match = classRegex.exec(content)) !== null) {
    classes.add(match[1]);
  }
  return classes;
}

/**
 * Extract method names from Java source files
 */
function extractJavaMethods(content, className) {
  const methods = new Set();
  const methodRegex = /public\s+(?:static\s+)?(?:<[^>]+>\s+)?(\w+(?:<[^>]+>)?)\s+(\w+)\s*\(/g;
  let match;
  while ((match = methodRegex.exec(content)) !== null) {
    const methodName = match[2];
    if (methodName !== className) {
      methods.add(methodName);
    }
  }
  return methods;
}

/**
 * Extract configuration properties from Properties class
 */
function extractConfigProperties(content, prefix = '') {
  const properties = new Set();
  const prefixMatch = content.match(/@ConfigurationProperties\s*\(\s*prefix\s*=\s*"([^"]+)"/);
  const basePrefix = prefixMatch ? prefixMatch[1] : prefix;

  const fieldRegex = /private\s+(?:boolean|int|long|String|String\[\]|Boolean|Integer|Long|Duration|\w+Properties|\w+)\s+(\w+)/g;
  let match;
  while ((match = fieldRegex.exec(content)) !== null) {
    const fieldName = match[1];
    const kebabName = fieldName.replace(/([A-Z])/g, '-$1').toLowerCase();
    if (basePrefix) {
      properties.add(`${basePrefix}.${kebabName}`);
    }
    properties.add(fieldName);
  }

  return { properties, prefix: basePrefix };
}

// Common example/sample class patterns to ignore
const EXAMPLE_CLASS_PATTERNS = [
  /^Custom\w+/,      // CustomUserDetailsService, CustomHandler
  /^My\w+/,          // MyService, MyController
  /^Sample\w+/,      // SampleService
  /^Example\w+/,     // ExampleHandler
  /^Demo\w+/,        // DemoController
  /^Test\w+/,        // TestService (in docs, not test code)
  /^Your\w+/,        // YourService
];

// External/Spring framework classes to ignore
const EXTERNAL_CLASSES = new Set([
  'UserDetailsService', 'PasswordEncoder', 'AuthenticationManager',
  'SecurityFilterChain', 'HttpSecurity', 'WebSecurityConfigurerAdapter',
  'OncePerRequestFilter', 'Authentication', 'UserDetails',
  'OAuth2User', 'OAuth2UserInfo', 'OAuth2UserRequest',
  'DataSource', 'JdbcTemplate', 'RedisTemplate',
  'LockProvider', 'JdbcTemplateLockProvider', // ShedLock
  'EncryptionService', // From simplix-encryption module
]);

/**
 * Check if a name looks like a variable (starts with lowercase)
 */
function isVariableName(name) {
  return /^[a-z]/.test(name);
}

/**
 * Check if a class name matches example patterns
 */
function isExampleClass(name) {
  return EXAMPLE_CLASS_PATTERNS.some(pattern => pattern.test(name));
}

/**
 * Extract class references from markdown documentation
 */
function extractDocClassReferences(content) {
  const classes = new Set();

  // Match class names in code blocks (class declarations only)
  const codeBlockRegex = /```java[\s\S]*?```/g;
  let blockMatch;
  while ((blockMatch = codeBlockRegex.exec(content)) !== null) {
    const block = blockMatch[0];
    const classInBlock = /(?:class|interface|enum)\s+(\w+)/g;
    let classMatch;
    while ((classMatch = classInBlock.exec(block)) !== null) {
      const className = classMatch[1];
      // Skip example classes and variable-like names
      if (!isExampleClass(className) && !isVariableName(className)) {
        classes.add(className);
      }
    }
  }

  // Match SimpliX class names in prose (more specific pattern)
  const simplixRegex = /\b(SimpliX[A-Z]\w+)\b/g;
  let match;
  while ((match = simplixRegex.exec(content)) !== null) {
    classes.add(match[1]);
  }

  // Match interface/class references with proper capitalization
  const serviceRegex = /\b([A-Z]\w*(?:Service|Provider|Handler|Filter|Controller|Repository|Configuration|Properties))\b/g;
  while ((match = serviceRegex.exec(content)) !== null) {
    const className = match[1];
    // Skip external, example classes, and variable-like names
    if (!EXTERNAL_CLASSES.has(className) &&
        !isExampleClass(className) &&
        !isVariableName(className)) {
      classes.add(className);
    }
  }

  return classes;
}

/**
 * Extract YAML property paths from documentation
 */
function extractDocYamlProperties(content) {
  const properties = new Set();
  const yamlBlockRegex = /```ya?ml([\s\S]*?)```/g;
  let blockMatch;

  while ((blockMatch = yamlBlockRegex.exec(content)) !== null) {
    const yaml = blockMatch[1];
    const lines = yaml.split('\n');
    const stack = [];

    for (const line of lines) {
      if (line.trim() === '' || line.trim().startsWith('#')) continue;

      const match = line.match(/^(\s*)(\w+[-\w]*):/);
      if (match) {
        const indent = match[1].length;
        const key = match[2];

        while (stack.length > 0 && stack[stack.length - 1].indent >= indent) {
          stack.pop();
        }

        stack.push({ key, indent });
        const path = stack.map(s => s.key).join('.');
        properties.add(path);
      }
    }
  }

  return properties;
}

/**
 * Extract method references from documentation
 */
function extractDocMethodReferences(content) {
  const methods = new Set();
  const codeBlockRegex = /```java([\s\S]*?)```/g;
  let blockMatch;

  while ((blockMatch = codeBlockRegex.exec(content)) !== null) {
    const block = blockMatch[1];
    const methodRegex = /(?:public\s+)?(?:static\s+)?(?:\w+(?:<[^>]+>)?)\s+(\w+)\s*\([^)]*\)/g;
    let methodMatch;
    while ((methodMatch = methodRegex.exec(block)) !== null) {
      const methodName = methodMatch[1];
      if (!['if', 'for', 'while', 'switch', 'catch', 'new'].includes(methodName)) {
        methods.add(methodName);
      }
    }
  }

  return methods;
}

async function validateModule(moduleName) {
  const result = new ValidationResult();
  const modulePath = join(PROJECT_ROOT, moduleName);
  const srcPath = join(modulePath, 'src/main/java');
  const docsPath = join(modulePath, 'docs/ko');

  if (!await fileExists(modulePath)) {
    result.addSkipped(`Module directory not found: ${moduleName}`);
    return result;
  }

  if (!await fileExists(docsPath)) {
    result.addSkipped(`No documentation found for ${moduleName}`);
    return result;
  }

  // Collect all Java classes and methods
  const javaFiles = await findJavaFiles(srcPath);
  const allClasses = new Set();
  const allMethods = new Map();
  const allProperties = new Map();

  for (const javaFile of javaFiles) {
    const content = await readFileContent(javaFile);
    if (!content) continue;

    const classes = extractJavaClasses(content);
    for (const cls of classes) {
      allClasses.add(cls);
      const methods = extractJavaMethods(content, cls);
      allMethods.set(cls, methods);
    }

    if (javaFile.includes('Properties.java') || content.includes('@ConfigurationProperties')) {
      const { properties, prefix } = extractConfigProperties(content);
      if (prefix) {
        if (!allProperties.has(prefix)) {
          allProperties.set(prefix, new Set());
        }
        for (const prop of properties) {
          allProperties.get(prefix).add(prop);
        }
      }
    }
  }

  // Collect all documentation references
  const mdFiles = await findMarkdownFiles(docsPath);
  const readmePath = join(modulePath, 'README.md');
  if (await fileExists(readmePath)) {
    mdFiles.push(readmePath);
  }

  for (const mdFile of mdFiles) {
    const content = await readFileContent(mdFile);
    if (!content) continue;

    const relativePath = relative(PROJECT_ROOT, mdFile);

    // Validate class references
    const docClasses = extractDocClassReferences(content);
    for (const cls of docClasses) {
      if (['String', 'Boolean', 'Integer', 'Long', 'Date', 'List', 'Set', 'Map',
           'UserDetails', 'Authentication', 'OncePerRequestFilter', 'UserDetailsService',
           'Instant', 'Duration', 'OAuth2UserInfo'].includes(cls)) {
        continue;
      }

      if (allClasses.has(cls)) {
        result.addPassed(`Class '${cls}' exists`);
      } else {
        if (cls.startsWith('SimpliX') || cls.endsWith('Service') || cls.endsWith('Provider')) {
          result.addWarning(`Class '${cls}' referenced in docs not found in ${moduleName}`, relativePath,
            'May be external or from another module');
        }
      }
    }

    // Validate configuration properties
    const docProps = extractDocYamlProperties(content);
    for (const prop of docProps) {
      let found = false;
      for (const [prefix, props] of allProperties) {
        if (prop.startsWith(prefix) || props.has(prop)) {
          found = true;
          break;
        }
        const segments = prop.split('.');
        const lastSegment = segments[segments.length - 1];
        const camelCase = lastSegment.replace(/-([a-z])/g, (_, c) => c.toUpperCase());
        if (props.has(camelCase) || props.has(lastSegment)) {
          found = true;
          break;
        }
      }

      if (found) {
        result.addPassed(`Property '${prop}' exists`);
      } else if (prop.startsWith('simplix.')) {
        result.addWarning(`Property '${prop}' not found in Properties classes`, relativePath);
      }
    }

    // Validate method references
    const docMethods = extractDocMethodReferences(content);
    for (const method of docMethods) {
      let found = false;
      for (const [cls, methods] of allMethods) {
        if (methods.has(method)) {
          found = true;
          result.addPassed(`Method '${method}' exists in ${cls}`);
          break;
        }
      }

      if (!found) {
        if (['issueTokenPair', 'getAuthentication', 'refreshToken', 'revokeToken',
             'blacklist', 'isBlacklisted', 'authenticateOAuth2User', 'linkSocialAccount',
             'unlinkSocialAccount', 'getLinkedProviders'].includes(method)) {
          result.addWarning(`Method '${method}' referenced in docs not found`, relativePath);
        }
      }
    }
  }

  return result;
}

function printResults(moduleName, result) {
  console.log(`\n${SYMBOLS.info} Validating ${moduleName}...`);

  const passedCount = result.passed.length;
  if (passedCount > 0) {
    console.log(`  ${SYMBOLS.success} ${passedCount} references verified`);
  }

  result.skipped.forEach(msg => {
    console.log(`  ${SYMBOLS.info} Skipped: ${msg}`);
  });

  result.warnings.forEach(({ message, file, detail }) => {
    const location = file ? ` (${file})` : '';
    const detailStr = detail ? ` - ${detail}` : '';
    console.log(`  ${SYMBOLS.warning} Warning: ${message}${location}${detailStr}`);
  });

  result.errors.forEach(({ message, file, detail }) => {
    const location = file ? ` (${file})` : '';
    const detailStr = detail ? ` - ${detail}` : '';
    console.log(`  ${SYMBOLS.error} Error: ${message}${location}${detailStr}`);
  });
}

async function main() {
  const args = process.argv.slice(2);
  const targetModule = args.find(arg => !arg.startsWith('--'));
  const showAll = args.includes('--all');
  const strictMode = args.includes('--strict');

  console.log(`${SYMBOLS.info} SimpliX Code-Documentation Sync Validator\n`);
  console.log(`Mode: ${strictMode ? 'Strict (warnings are errors)' : 'Normal'}`);
  console.log('-'.repeat(60));

  let totalErrors = 0;
  let totalWarnings = 0;
  let totalPassed = 0;
  let modulesValidated = 0;

  const modulesToValidate = showAll
    ? MODULES
    : targetModule
      ? MODULES.filter(m => m.includes(targetModule))
      : MODULES;

  if (targetModule && modulesToValidate.length === 0) {
    console.log(`${SYMBOLS.error} No modules found matching: ${targetModule}`);
    process.exit(1);
  }

  for (const moduleName of modulesToValidate) {
    const result = await validateModule(moduleName);

    if (result.skipped.length === 0 || showAll) {
      printResults(moduleName, result);
      modulesValidated++;
    }

    totalErrors += result.errors.length;
    totalWarnings += result.warnings.length;
    totalPassed += result.passed.length;
  }

  console.log(`\n${'-'.repeat(60)}`);
  console.log(`${SYMBOLS.info} Summary`);
  console.log(`  Modules validated: ${modulesValidated}`);
  console.log(`  ${SYMBOLS.success} Verified: ${totalPassed}`);
  console.log(`  ${SYMBOLS.warning} Warnings: ${totalWarnings}`);
  console.log(`  ${SYMBOLS.error} Errors: ${totalErrors}`);

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