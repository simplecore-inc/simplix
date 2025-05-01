import { execSync } from 'child_process';

export function getGitConfig(key) {
  try {
    return execSync(`git config ${key}`).toString().trim();
  } catch (error) {
    return key === 'user.name' ? 'Unknown' : 'unknown@example.com';
  }
}
