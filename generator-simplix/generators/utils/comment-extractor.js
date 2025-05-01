export function extractComment(entityContent, fieldName) {
  const commentRegex = new RegExp(`@Comment\\s*\\("([^"]+)"\\)\\s*(?:private|public)\\s+(?:[\\w<>]+)\\s+${fieldName}`);
  const match = entityContent.match(commentRegex);

  if (match) return match[1];
  return camelToWords(fieldName);
}

export function extractTableComment(entityContent) {
  const tableCommentRegex =
    /@org\.hibernate\.annotations\.Table\s*\(\s*appliesTo\s*=\s*"[^"]+"\s*,\s*comment\s*=\s*"([^"]+)"\s*\)/;
  const match = entityContent.match(tableCommentRegex);

  if (match) return match[1];
  return generateDefaultTableComment(entityContent);
}

function camelToWords(str) {
  return str
    .replace(/([A-Z])/g, ' $1')
    .replace(/^./, str => str.toUpperCase())
    .trim();
}

function generateDefaultTableComment(entityContent) {
  const tableNameRegex = /@Table\s*\(\s*name\s*=\s*"([^"]+)"\s*\)/;
  const tableMatch = entityContent.match(tableNameRegex);
  if (tableMatch) {
    return tableMatch[1]
      .split('_')
      .map(word => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
      .join(' ');
  }

  return 'Entity Table';
}
