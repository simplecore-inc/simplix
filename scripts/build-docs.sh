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

# Function to convert module name to target directory name
# e.g., simplix-core -> core, spring-boot-starter-simplix -> starter
get_target_name() {
  local module="$1"
  if [[ "$module" == "spring-boot-starter-simplix" ]]; then
    echo "starter"
  else
    # Remove 'simplix-' prefix
    echo "${module#simplix-}"
  fi
}

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

# 4. Auto-discover and copy module documentation
echo "Copying module documentation..."

# Store module mappings for link conversion (module_name:target_name)
declare -a MODULE_MAPPINGS=()

for module_docs_dir in */docs/ko; do
  if [ -d "$module_docs_dir" ]; then
    module=$(dirname "$(dirname "$module_docs_dir")")
    target=$(get_target_name "$module")

    mkdir -p "$BUILD_DIR/ko/$target"
    cp -r "$module_docs_dir/"* "$BUILD_DIR/ko/$target/" 2>/dev/null || true
    echo "  Copied: $module/docs/ko -> ko/$target"

    MODULE_MAPPINGS+=("$module:$target")
  fi
done

# 4.5. Auto-discover and copy module README.md files
echo "Copying module README files..."

for readme in */README.md; do
  if [ -f "$readme" ]; then
    module=$(dirname "$readme")
    # Skip root README and non-module directories
    if [[ "$module" != "." && -d "$module/src" ]]; then
      target=$(get_target_name "$module")
      mkdir -p "$BUILD_DIR/ko/$target"
      cp "$readme" "$BUILD_DIR/ko/$target/readme.md"
      echo "  Copied: $readme -> ko/$target/readme.md"
    fi
  fi
done

# 5. Link conversion function
convert_links() {
  local file="$1"

  # Get the directory path relative to build-docs (e.g., ko/core, ko/excel)
  local dir_path
  dir_path=$(dirname "${file#$BUILD_DIR/}")

  # Convert ./ relative links to absolute paths based on file location
  # e.g., ./overview.md in ko/core/ becomes ko/core/overview.md
  sed -i.bak "s|(\./|($dir_path/|g" "$file" && rm -f "${file}.bak"

  # Build sed expressions dynamically based on discovered modules
  local sed_args=()

  # Common link conversions
  sed_args+=(-e 's|(docs/ko/|(|g')
  sed_args+=(-e 's|(/ko/|(ko/|g')
  sed_args+=(-e 's|\.\./\.\./README\.md|ko/README.md|g')
  sed_args+=(-e 's|\.\./\.\./LICENSE|https://github.com/simplecore-inc/simplix/blob/main/LICENSE|g')

  # Module-specific link conversions
  for mapping in "${MODULE_MAPPINGS[@]}"; do
    local module="${mapping%%:*}"
    local target="${mapping##*:}"

    # ../../module/docs/ko/ -> ko/target/
    sed_args+=(-e "s|\\.\\./\\.\\./$(echo "$module" | sed 's/[.[\*^$()+?{|]/\\&/g')/docs/ko/|ko/$target/|g")

    # ../../module/README.md -> ko/target/readme.md
    sed_args+=(-e "s|\\.\\./\\.\\./$(echo "$module" | sed 's/[.[\*^$()+?{|]/\\&/g')/README\\.md|ko/$target/readme.md|g")

    # ../../../module/ -> ko/target/readme.md
    sed_args+=(-e "s|\\.\\./\\.\\./\\.\\./$(echo "$module" | sed 's/[.[\*^$()+?{|]/\\&/g')/|ko/$target/readme.md|g")
  done

  sed "${sed_args[@]}" "$file" > "${file}.tmp" && mv "${file}.tmp" "$file"
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