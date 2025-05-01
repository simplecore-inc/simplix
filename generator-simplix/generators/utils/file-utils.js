import fs from 'graceful-fs';
import path from 'path';

export function findEntityFile(srcPath, entityName) {
  try {
    const files = fs.readdirSync(srcPath);
    for (const file of files) {
      const filePath = path.join(srcPath, file);
      const stat = fs.statSync(filePath);
      if (stat.isDirectory()) {
        const found = findEntityFile(filePath, entityName);
        if (found) return found;
      } else if (file === `${entityName}.java`) {
        return filePath;
      }
    }
  } catch (error) {
    console.error(`Error scanning directory ${srcPath}:`, error.message);
  }

  return null;
}

export function validateProjectStructure(srcPath) {
  if (!fs.existsSync(srcPath)) {
    throw new Error(`Invalid project structure. Expected path: ${srcPath}`);
  }

  // Navigate from src/main/java to project root
  const projectRoot = path.resolve(srcPath, '../../..');
  const gradleBuildFile = path.join(projectRoot, 'build.gradle');
  const mavenPomFile = path.join(projectRoot, 'pom.xml');

  // Check for either Gradle or Maven build file
  if (!fs.existsSync(gradleBuildFile) && !fs.existsSync(mavenPomFile)) {
    throw new Error(`No build file found in ${projectRoot}. Expected either build.gradle (Gradle) or pom.xml (Maven)`);
  }

  try {
    const stats = fs.statSync(srcPath);
    if (!stats.isDirectory()) {
      throw new Error('src/main/java is not a directory');
    }
  } catch (error) {
    throw new Error(`Error validating project structure: ${error.message}`);
  }

  return {
    projectRoot,
    buildSystem: fs.existsSync(gradleBuildFile) ? 'gradle' : 'maven',
    buildFile: fs.existsSync(gradleBuildFile) ? gradleBuildFile : mavenPomFile,
  };
}
