import { readFileSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

export function getGeneratorVersion() {
  try {
    const __dirname = dirname(fileURLToPath(import.meta.url));
    const packageJson = JSON.parse(readFileSync(join(__dirname, '../../package.json'), 'utf8'));
    return packageJson.version;
  } catch (error) {
    console.error('Failed to read package.json:', error);
    return '0.0.0';
  }
}
