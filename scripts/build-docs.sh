#!/usr/bin/env bash
# SimpliX Documentation Build Script
# Copies and transforms documentation for Docsify deployment
set -e

BUILD_DIR="build-docs"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_ROOT"

# Use DOCS_VERSION env var if set, otherwise extract from build.gradle
if [ -n "$DOCS_VERSION" ]; then
  VERSION="$DOCS_VERSION"
else
  VERSION=$(grep "projectVersion = " build.gradle | sed "s/.*projectVersion = '\([^']*\)'.*/\1/")
fi
echo "Building SimpliX documentation... (v${VERSION})"

# 1. Initialize build directory
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/ko"

# 2. Copy Docsify configuration files
echo "Copying Docsify configuration files..."
cp docs/index.html "$BUILD_DIR/"
cp docs/.nojekyll "$BUILD_DIR/"
cp docs/_coverpage.md "$BUILD_DIR/"
cp docs/_navbar.md "$BUILD_DIR/"
cp docs/ko/_sidebar.md "$BUILD_DIR/ko/"
cp docs/ko/_sidebar.md "$BUILD_DIR/_sidebar.md"

# 2.5. Replace version placeholder (escape hyphens for shields.io badge)
VERSION_ESCAPED=$(echo "$VERSION" | sed 's/-/--/g')
sed -i.bak "s/{{VERSION}}/${VERSION_ESCAPED}/g" "$BUILD_DIR/_coverpage.md" && rm -f "$BUILD_DIR/_coverpage.md.bak"

# 3. Copy tutorial documents from docs/ko/
echo "Copying tutorial documents..."
find docs/ko -maxdepth 1 -name "*.md" -type f -exec cp {} "$BUILD_DIR/ko/" \;

# 4. Copy module documentation
echo "Copying module documentation..."

copy_module_docs() {
  local module="$1"
  local target="$2"

  if [ -d "$module/docs/ko" ]; then
    mkdir -p "$BUILD_DIR/ko/$target"
    cp -r "$module/docs/ko/"* "$BUILD_DIR/ko/$target/" 2>/dev/null || true
    echo "  Copied: $module -> ko/$target"
  fi
}

copy_module_docs "simplix-core" "core"
copy_module_docs "simplix-auth" "auth"
copy_module_docs "simplix-cache" "cache"
copy_module_docs "simplix-email" "email"
copy_module_docs "simplix-encryption" "encryption"
copy_module_docs "simplix-event" "event"
copy_module_docs "simplix-excel" "excel"
copy_module_docs "simplix-file" "file"
copy_module_docs "simplix-hibernate" "hibernate"
copy_module_docs "simplix-mybatis" "mybatis"
copy_module_docs "spring-boot-starter-simplix" "starter"

# 4.5. Copy module README.md files
echo "Copying module README files..."

copy_module_readme() {
  local module="$1"
  local target="$2"

  if [ -f "$module/README.md" ]; then
    mkdir -p "$BUILD_DIR/ko/$target"
    cp "$module/README.md" "$BUILD_DIR/ko/$target/readme.md"
    echo "  Copied: $module/README.md -> ko/$target/readme.md"
  fi
}

copy_module_readme "simplix-core" "core"
copy_module_readme "simplix-auth" "auth"
copy_module_readme "simplix-cache" "cache"
copy_module_readme "simplix-email" "email"
copy_module_readme "simplix-encryption" "encryption"
copy_module_readme "simplix-event" "event"
copy_module_readme "simplix-excel" "excel"
copy_module_readme "simplix-file" "file"
copy_module_readme "simplix-hibernate" "hibernate"
copy_module_readme "simplix-mybatis" "mybatis"
copy_module_readme "spring-boot-starter-simplix" "starter"

# 5. Link conversion function
convert_links() {
  local file="$1"

  # Convert module cross-references to Docsify absolute paths
  # Also convert README.md internal links (docs/ko/ -> ./)
  sed \
    -e 's|(docs/ko/|(./|g' \
    -e 's|\.\./\.\./simplix-core/docs/ko/|/ko/core/|g' \
    -e 's|\.\./\.\./simplix-auth/docs/ko/|/ko/auth/|g' \
    -e 's|\.\./\.\./simplix-cache/docs/ko/|/ko/cache/|g' \
    -e 's|\.\./\.\./simplix-email/docs/ko/|/ko/email/|g' \
    -e 's|\.\./\.\./simplix-encryption/docs/ko/|/ko/encryption/|g' \
    -e 's|\.\./\.\./simplix-event/docs/ko/|/ko/event/|g' \
    -e 's|\.\./\.\./simplix-excel/docs/ko/|/ko/excel/|g' \
    -e 's|\.\./\.\./simplix-file/docs/ko/|/ko/file/|g' \
    -e 's|\.\./\.\./simplix-hibernate/docs/ko/|/ko/hibernate/|g' \
    -e 's|\.\./\.\./simplix-mybatis/docs/ko/|/ko/mybatis/|g' \
    -e 's|\.\./\.\./spring-boot-starter-simplix/docs/ko/|/ko/starter/|g' \
    -e 's|\.\./\.\./simplix-core/README\.md|/ko/core/overview.md|g' \
    -e 's|\.\./\.\./simplix-auth/README\.md|/ko/auth/getting-started.md|g' \
    -e 's|\.\./\.\./simplix-cache/README\.md|/ko/cache/overview.md|g' \
    -e 's|\.\./\.\./simplix-email/README\.md|/ko/email/overview.md|g' \
    -e 's|\.\./\.\./simplix-encryption/README\.md|/ko/encryption/overview.md|g' \
    -e 's|\.\./\.\./simplix-event/README\.md|/ko/event/overview.md|g' \
    -e 's|\.\./\.\./simplix-excel/README\.md|/ko/excel/overview.md|g' \
    -e 's|\.\./\.\./simplix-file/README\.md|/ko/file/overview.md|g' \
    -e 's|\.\./\.\./simplix-hibernate/README\.md|/ko/hibernate/overview.md|g' \
    -e 's|\.\./\.\./simplix-mybatis/README\.md|/ko/mybatis/overview.md|g' \
    -e 's|\.\./\.\./spring-boot-starter-simplix/README\.md|/ko/starter/overview.md|g' \
    -e 's|\.\./\.\./README\.md|/ko/README.md|g' \
    -e 's|\.\./\.\./LICENSE|https://github.com/simplecore-inc/simplix/blob/main/LICENSE|g' \
    -e 's|\.\./\.\./\.\./simplix-core/|/ko/core/overview.md|g' \
    -e 's|\.\./\.\./\.\./simplix-auth/|/ko/auth/getting-started.md|g' \
    -e 's|\.\./\.\./\.\./simplix-cache/|/ko/cache/overview.md|g' \
    -e 's|\.\./\.\./\.\./simplix-email/|/ko/email/overview.md|g' \
    -e 's|\.\./\.\./\.\./simplix-encryption/|/ko/encryption/overview.md|g' \
    -e 's|\.\./\.\./\.\./simplix-event/|/ko/event/overview.md|g' \
    -e 's|\.\./\.\./\.\./simplix-excel/|/ko/excel/overview.md|g' \
    -e 's|\.\./\.\./\.\./simplix-file/|/ko/file/overview.md|g' \
    -e 's|\.\./\.\./\.\./simplix-hibernate/|/ko/hibernate/overview.md|g' \
    -e 's|\.\./\.\./\.\./simplix-mybatis/|/ko/mybatis/overview.md|g' \
    -e 's|\.\./\.\./\.\./spring-boot-starter-simplix/|/ko/starter/overview.md|g' \
    "$file" > "${file}.tmp" && mv "${file}.tmp" "$file"
}

# 6. Apply link conversion to all markdown files
echo "Converting links..."
find "$BUILD_DIR" -name "*.md" -type f | while read -r file; do
  convert_links "$file"
done

# 7. Count files
total_files=$(find "$BUILD_DIR" -name "*.md" -type f | wc -l | tr -d ' ')

echo ""
echo "Documentation build completed!"
echo "  Output directory: $BUILD_DIR"
echo "  Total markdown files: $total_files"
echo ""
echo "To preview locally:"
echo "  cd $BUILD_DIR && python -m http.server 3000"
echo "  Then open http://localhost:3000"
