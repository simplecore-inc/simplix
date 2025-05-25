/* eslint-disable max-depth */
import { extractComment } from '../utils/comment-extractor.js';

export function analyzeEntity(entityContent) {
  // Extract ID field information
  const { idType, embeddedIdField, idField } = extractIdInfo(entityContent);

  // Extract package and imports
  const packageMatch = entityContent.match(/package\s+([\w.]+);/);
  const basePackage = packageMatch ? packageMatch[1].substring(0, packageMatch[1].lastIndexOf('.')) : '';
  const imports = analyzeImports(entityContent);

  // Analyze regular fields
  const contentLines = entityContent.split('\n');
  const analyzedFields = analyzeFields(contentLines, imports, basePackage, entityContent);

  // Analyze embeddedId fields if present
  const embeddedIdFields = analyzeEmbeddedId(entityContent, embeddedIdField, idType);

  // Process imports for entity references
  const importInfo = processImports(entityContent);

  return {
    fields: analyzedFields,
    idType,
    idField,
    imports: importInfo,
    embeddedIdFields,
    embeddedIdField,
  };
}

function extractIdInfo(entityContent) {
  let idType = 'String';
  let embeddedIdField = null;
  let idField = null;

  const contentLines = entityContent.split('\n');
  let isIdField = false;
  let isEmbeddedIdField = false;

  // ID field analysis
  for (const line of contentLines) {
    const trimmedLine = line.trim();
    if (trimmedLine === '@Id') {
      isIdField = true;
      continue;
    }

    if (trimmedLine === '@EmbeddedId') {
      isEmbeddedIdField = true;
      continue;
    }

    if ((isIdField || isEmbeddedIdField) && trimmedLine.startsWith('private ')) {
      const fieldMatch = trimmedLine.match(/private\s+([^\s<>]+)(?:<[^>]+>)?\s+(\w+)/);
      if (fieldMatch) {
        if (isEmbeddedIdField) {
          embeddedIdField = fieldMatch[2]; // field name
          idType = fieldMatch[1]; // field type
          idField = embeddedIdField;
          console.log(`Found EmbeddedId field: ${embeddedIdField} of type ${idType}`);
        } else {
          idType = fieldMatch[1];
          idField = fieldMatch[2];
        }

        break;
      }

      isIdField = false;
      isEmbeddedIdField = false;
    }

    if ((isIdField || isEmbeddedIdField) && trimmedLine.startsWith('@')) {
      continue;
    } else if ((isIdField || isEmbeddedIdField) && trimmedLine.length > 0 && !trimmedLine.startsWith('@')) {
      isIdField = false;
      isEmbeddedIdField = false;
    }
  }

  return { idType, embeddedIdField, idField };
}

function processImports(entityContent) {
  const importLines = entityContent.match(/import.*?;/gm) || [];
  const importInfo = {};
  importLines.forEach(line => {
    if (line.includes('.entity.')) {
      const entityName = line.split('.').pop().replace(';', '');
      importInfo[entityName] = line.replace('import ', '').replace(';', '');
    }
  });
  return importInfo;
}

function analyzeEmbeddedId(entityContent, embeddedIdField, idType) {
  if (!embeddedIdField) {
    return null;
  }

  console.log(`EmbeddedId field name: ${embeddedIdField}`);

  // 외부 클래스 참조인 경우
  if (idType.includes('.')) {
    // 외부 클래스 참조 - 아직 구현되지 않음
    return null;
  }

  // 내부 클래스에서 @Embeddable 어노테이션 찾기
  const staticClassPattern = new RegExp(
    `@Embeddable[\\s\\S]*?static\\s+class\\s+${idType}[\\s\\S]*?\\{([\\s\\S]*?)\\}\\s*\\}`
  );
  const innerClassMatch = entityContent.match(staticClassPattern);

  if (!innerClassMatch) {
    return tryAlternativePatterns(entityContent, embeddedIdField, idType);
  }

  console.log('Found inner class with @Embeddable annotation');
  const innerClassContent = innerClassMatch[1];

  // regex를 통한 필드 추출
  return extractEmbeddedIdFields(innerClassContent, embeddedIdField);
}

function extractEmbeddedIdFields(innerClassContent, embeddedIdField) {
  const embeddedIdFields = [];

  // 두 가지 패턴으로 필드 추출 시도
  // 1. @Column 어노테이션이 있는 필드 추출
  const columnFieldRegex = /@Column[^)]+\)[^@]*?private\s+([^\s<>]+)(?:<[^>]+>)?\s+(\w+)/g;
  let fieldMatch;

  while ((fieldMatch = columnFieldRegex.exec(innerClassContent)) !== null) {
    const type = fieldMatch[1];
    const name = fieldMatch[2];

    // Extract comment if available
    let comment = name;
    const commentMatch = innerClassContent.match(
      new RegExp(`@Comment\\(["']([^"']+)["']\\)[^@]*?private\\s+[^\\s<>]+\\s+${name}`)
    );
    if (commentMatch) {
      comment = commentMatch[1];
    }

    embeddedIdFields.push({
      name,
      type,
      typeKind: 'basic',
      comment,
      parentField: embeddedIdField,
    });
  }

  // 2. @Column이 없는 경우 모든 private 필드 추출
  if (embeddedIdFields.length === 0) {
    const privateFieldRegex = /private\s+([^\s<>]+)(?:<[^>]+>)?\s+(\w+)/g;

    while ((fieldMatch = privateFieldRegex.exec(innerClassContent)) !== null) {
      const type = fieldMatch[1];
      const name = fieldMatch[2];

      embeddedIdFields.push({
        name,
        type,
        typeKind: 'basic',
        comment: name, // 기본적으로 필드명을 코멘트로 사용
        parentField: embeddedIdField,
      });
    }
  }

  if (embeddedIdFields.length > 0) {
    console.log(`Found ${embeddedIdFields.length} fields in embedded ID class:`);
    embeddedIdFields.forEach(f => console.log(`  - ${f.name} (${f.type})`));
  }

  return embeddedIdFields.length > 0 ? embeddedIdFields : null;
}

function tryAlternativePatterns(entityContent, embeddedIdField, idType) {
  console.log('Inner class not found with first pattern, trying alternative');

  // 일반적인 패턴으로 시도
  const generalPattern = new RegExp(`class\\s+${idType}[\\s\\S]*?\\{([\\s\\S]*?)\\}\\s*\\}`);
  const generalMatch = entityContent.match(generalPattern);

  if (!generalMatch) {
    return null;
  }

  console.log('Found inner class with general pattern');
  const innerClassContent = generalMatch[1];

  // extractEmbeddedIdFields 함수를 재사용하여 필드 추출
  return extractEmbeddedIdFields(innerClassContent, embeddedIdField);
}

function analyzeImports(entityContent) {
  const imports = new Map();
  const importRegex = /import\s+([^;]+);/g;
  let importMatch;

  while ((importMatch = importRegex.exec(entityContent)) !== null) {
    const importPath = importMatch[1].trim();
    const typeName = importPath.split('.').pop();
    const packagePath = importPath.substring(0, importPath.lastIndexOf('.'));
    const packageParts = packagePath.split('.');

    const isEnum = packageParts.includes('enums') || entityContent.includes(`enum ${typeName}`);
    const isEntityPackage = packageParts.includes('entity');
    const hasEntityAnnotation = entityContent.includes(`@Entity\nclass ${typeName}`);
    const isReferencedAsEntity = hasEntityRelation(entityContent);

    imports.set(typeName, {
      path: importPath,
      package: packagePath,
      isEntity: !isEnum && (isEntityPackage || hasEntityAnnotation || isReferencedAsEntity),
      isEnum,
    });
  }

  return imports;
}

function hasEntityRelation(content) {
  return (
    content.includes('@ManyToOne') ||
    content.includes('@OneToMany') ||
    content.includes('@OneToOne') ||
    content.includes('@ManyToMany')
  );
}

function analyzeFields(contentLines, imports, basePackage, entityContent) {
  const fields = [];
  let currentFieldAnnotations = [];
  let currentField = null;

  for (const line of contentLines) {
    const trimmedLine = line.trim();

    // 주석 처리
    if (trimmedLine.startsWith('@Comment')) {
      const commentMatch = trimmedLine.match(/@Comment\("([^"]+)"\)/);
      if (commentMatch) {
        currentField = { comment: commentMatch[1] };
      }

      continue;
    }

    // 관계 어노테이션 처리
    if (trimmedLine.startsWith('@')) {
      currentFieldAnnotations.push(trimmedLine);
      continue;
    }

    // 필드 선언 처리
    if (trimmedLine.startsWith('private ')) {
      const field = extractFieldInfo({
        line,
        annotations: currentFieldAnnotations,
        imports,
        basePackage,
        entityContent,
      });

      if (field) {
        // @Id 어노테이션 체크
        field.isId = currentFieldAnnotations.some(a => a === '@Id');

        // 모든 관계 어노테이션 처리
        const relationType = getRelationType(currentFieldAnnotations);
        if (relationType) {
          field.typeKind = 'entity';
          field.isRelation = true;
          field.relationType = relationType;

          // actualType에서 엔티티 타입 추출 (컬렉션인 경우 제네릭 타입)
          const entityType = field.isCollection ? field.actualType : field.actualType;

          // import 경로 찾기
          const importMatch = entityContent.match(new RegExp(`import\\s+([\\w.]+\\.entity\\.${entityType});`));
          if (importMatch) {
            field.importPath = importMatch[1];
            field.importPackage = importMatch[1].substring(0, importMatch[1].lastIndexOf('.'));
          }
        }

        if (currentField) {
          field.comment = currentField.comment;
        }

        fields.push(field);
      }

      currentFieldAnnotations = [];
      currentField = null;
    }
  }

  return fields;
}

function getRelationType(annotations) {
  if (annotations.some(a => a.includes('@ManyToOne'))) return 'ManyToOne';
  if (annotations.some(a => a.includes('@OneToMany'))) return 'OneToMany';
  if (annotations.some(a => a.includes('@ManyToMany'))) return 'ManyToMany';
  if (annotations.some(a => a.includes('@OneToOne'))) return 'OneToOne';
  return null;
}

function extractFieldInfo({ line, annotations, imports, basePackage, entityContent }) {
  // Java 컬렉션 타입들
  const collectionTypes = [
    'Set',
    'List',
    'Collection',
    'Iterable',
    'Queue',
    'Deque',
    'ArrayList',
    'LinkedList',
    'HashSet',
    'TreeSet',
    'LinkedHashSet',
    'Map',
  ].join('|');

  // Map 타입을 먼저 체크
  const mapMatch = line.match(/private\s+(Map\s*<[^>]+>)\s+(\w+)/);
  if (mapMatch) {
    const [, fullType, name] = mapMatch;
    // HTML 엔티티 디코딩
    const decodedType = fullType.trim().replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&amp;/g, '&');
    return {
      name,
      type: decodedType,
      actualType: name,
      typeKind: 'basic',
      isCollection: true,
      collectionType: 'Map',
      comment: extractComment(entityContent, name) || name,
      importPath: '',
      importPackage: basePackage,
      isRelation: false,
      relationType: null,
    };
  }

  const fieldMatch = line.match(
    new RegExp(`private\\s+(?:(?:${collectionTypes})<)?([^\\s<>]+)(?:<([^>]+)>)?\\s+(\\w+)`)
  );
  if (!fieldMatch) return null;

  const [, type, genericType, name] = fieldMatch;
  const collectionMatch = line.match(new RegExp(`(${collectionTypes})<`));
  const isCollection = Boolean(collectionMatch);
  const baseType = isCollection ? genericType : type;
  const collectionType = collectionMatch ? collectionMatch[1] : null;

  let importInfo = imports.get(baseType);
  let typeKind = 'basic';
  let actualType = baseType;

  const relationType = getRelationType(annotations);
  const isRelation = Boolean(relationType);

  // Java 내장 타입들을 기본 타입으로 처리
  const javaBuiltInTypes = [
    'LocalDateTime',
    'LocalDate',
    'LocalTime',
    'ZonedDateTime',
    'OffsetDateTime',
    'Instant',
    'Duration',
    'Period',
    'UUID',
    'BigDecimal',
    'BigInteger',
  ];

  if (javaBuiltInTypes.includes(baseType)) {
    typeKind = 'basic';
    // 컬렉션인 경우 전체 타입을 반환
    const finalType = isCollection ? `${collectionType}<${baseType}>` : baseType;
    return {
      name,
      type: finalType,
      actualType,
      typeKind,
      isCollection,
      collectionType,
      comment: extractComment(entityContent, name) || name,
      importPath: importInfo?.path || '',
      importPackage: importInfo?.package || basePackage,
      isRelation,
      relationType,
    };
  }

  // @ManyToOne 어노테이션이 있으면 무조건 entity로 처리
  if (isRelation) {
    typeKind = 'entity';
    // import 경로 찾기
    const importMatch = entityContent.match(new RegExp(`import\\s+([\\w.]+\\.entity\\.${baseType});`));
    if (importMatch) {
      importInfo = {
        path: importMatch[1],
        package: importMatch[1].substring(0, importMatch[1].lastIndexOf('.')),
        isEntity: true,
      };
    }
  } else if (importInfo?.isEntity) {
    typeKind = 'entity';
  } else if (importInfo?.isEnum) {
    typeKind = 'enum';
  }

  // 최종 타입 결정: 컬렉션인 경우 전체 타입 반환
  const finalType = isCollection ? `${collectionType}<${baseType}>` : baseType;

  return {
    name,
    type: finalType,
    actualType,
    typeKind,
    isCollection,
    collectionType,
    comment: extractComment(entityContent, name) || name,
    importPath: importInfo?.path || '',
    importPackage: importInfo?.package || basePackage,
    isRelation,
    relationType,
  };
}
