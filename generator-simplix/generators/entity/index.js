process.removeAllListeners('warning');

process.on('warning', warning => {
  if (
    warning.name !== 'DeprecationWarning' ||
    (!warning.message.includes('fs.Stats') && !warning.message.includes('punycode'))
  ) {
    console.warn(warning);
  }
});

process.env.NODE_NO_WARNINGS = 1;
process.env.NO_DEPRECATION = '*';

import Generator from 'yeoman-generator';
import path from 'path';
import fs from 'graceful-fs';
import lodash from 'lodash';
import { analyzeEntity } from '../utils/entity-analyzer.js';
import { extractTableComment } from '../utils/comment-extractor.js';
import { findEntityFile, validateProjectStructure } from '../utils/file-utils.js';
import { getGitConfig } from '../utils/git-utils.js';
import { getGeneratorVersion } from '../utils/version-utils.js';
import yaml from 'js-yaml';
import findUp from 'find-up';
import ejs from 'ejs';

/**
 * Helper function to capitalize the first letter of a string
 */
function capitalizeFirstLetter(string) {
  if (!string) return '';
  return string.charAt(0).toUpperCase() + string.slice(1);
}

export default class extends Generator {
  constructor(args, opts) {
    super(args, opts);

    this.argument('entityName', {
      type: String,
      required: true,
      description: 'Name of the entity to generate CRUD files for',
    });

    this.option('force', {
      type: Boolean,
      default: false,
      description: 'Force overwrite existing files',
    });

    this.props = {
      gitUser: getGitConfig('user.name'),
      gitEmail: getGitConfig('user.email'),
      generatorVersion: getGeneratorVersion(),
    };
  }

  async initializing() {
    // Check if .simplix directory exists
    const simplixPath = this.destinationPath('.simplix');
    if (!fs.existsSync(simplixPath)) {
      throw new Error('The .simplix directory is missing. Please run the app generator first.');
    }

    // Read entity yml file
    const entityYmlPath = this.destinationPath(`.simplix/entity/${this.options.entityName}.yml`);
    if (fs.existsSync(entityYmlPath)) {
      try {
        this.entityConfig = yaml.load(fs.readFileSync(entityYmlPath, 'utf8'));
      } catch (e) {
        throw new Error(`Failed to read ${this.options.entityName}.yml: ${e.message}`);
      }
    }

    // Find .simplix directory path
    this.simplixPath = findUp.sync('.simplix', { type: 'directory' });
    if (!this.simplixPath) {
      throw new Error('.simplix directory not found');
    }

    // Set generator template path
    this.generatorTemplatePath = path.join(this.simplixPath, 'templates');

    // Read generator-simplix.json configuration
    const configPath = path.join(this.simplixPath, 'generator-simplix.json');
    try {
      const config = JSON.parse(fs.readFileSync(configPath, 'utf8'));

      if (!config.baseTargetPath) {
        throw new Error('baseTargetPath is not configured in generator-simplix.json');
      }

      this.baseTargetPath = config.baseTargetPath;
      this.basePackage = this.baseTargetPath.split('/java/')[1].replace(/\//g, '.');

      this.targetPaths = {};
      const targetPathKeys = {
        repository: 'repository',
        service: 'service',
        controllerRest: 'controllerRest',
        controllerWeb: 'controllerWeb',
        dto: 'dto',
      };

      Object.entries(config.targetPath || {}).forEach(([key, value]) => {
        const mappedKey = targetPathKeys[key] || key;
        this.targetPaths[mappedKey] = value;
      });
    } catch (e) {
      throw new Error(`Failed to read generator-simplix.json: ${e.message}`);
    }

    // Read modulePath from entity yml file
    const modulePath = this.entityConfig?.modulePath;
    if (!modulePath) {
      throw new Error(`modulePath is not configured in ${this.options.entityName}.yml`);
    }

    // Calculate package paths and template path
    const packagePaths = {};
    const templatePath =
      this.entityConfig?.thymeleafBaseDir ||
      lodash.kebabCase(this.options.entityName.replace(/([a-z])([A-Z])/g, '$1-$2').toLowerCase());

    Object.entries(this.targetPaths).forEach(([key, path]) => {
      packagePaths[key] = `${this.basePackage}.${path.replace('{modulePath}', modulePath).replace(/\//g, '.')}`;
    });

    // Configure files to generate
    this.filesToGenerate = [
      {
        template: path.join(this.generatorTemplatePath, 'repository/EntityRepository.java.template'),
        path: path.join(
          process.cwd(),
          this.baseTargetPath,
          this.targetPaths.repository.replace('{modulePath}', modulePath),
          `${this.options.entityName}Repository.java`
        ),
      },
      {
        template: path.join(this.generatorTemplatePath, 'service/EntityService.java.template'),
        path: path.join(
          process.cwd(),
          this.baseTargetPath,
          this.targetPaths.service.replace('{modulePath}', modulePath),
          `${this.options.entityName}Service.java`
        ),
      },
      {
        template: path.join(this.generatorTemplatePath, 'controller/rest/EntityRestController.java.template'),
        path: path.join(
          process.cwd(),
          this.baseTargetPath,
          this.targetPaths.controllerRest.replace('{modulePath}', modulePath),
          `${this.options.entityName}RestController.java`
        ),
      },
      {
        template: path.join(this.generatorTemplatePath, 'controller/web/EntityController.java.template'),
        path: path.join(
          process.cwd(),
          this.baseTargetPath,
          this.targetPaths.controllerWeb.replace('{modulePath}', modulePath),
          `${this.options.entityName}Controller.java`
        ),
      },
      {
        template: path.join(this.generatorTemplatePath, 'dto/EntityDTOs.java.template'),
        path: path.join(
          process.cwd(),
          this.baseTargetPath,
          this.targetPaths.dto.replace('{modulePath}', modulePath),
          `${this.options.entityName}DTOs.java`
        ),
      },
    ];

    this.props = {
      ...this.props,
      entityName: this.options.entityName,
      basePackage: this.basePackage,
      modulePath,
      targetPaths: this.targetPaths,
      packagePaths,
      templatePath,
    };
  }

  async prompting() {
    const { entityName } = this.options;

    if (!entityName) {
      this.log('\n🚫 Error: Entity name is required');
      this.log('Usage: yo simplix:entity <EntityName> [--force]');
      process.exit(1);
    }

    const srcPath = path.join(process.cwd(), 'src/main/java');
    validateProjectStructure(srcPath);

    const entityPath = findEntityFile(srcPath, entityName);
    if (!entityPath) {
      this.log('\n🚫 Error: Entity file not found');
      this.log(`Looking for: ${entityName}.java`);
      this.log(`Searched in: ${srcPath}`);
      process.exit(1);
    }

    this.log(`\n✅ Found entity file:\n    ${path.relative(process.cwd(), entityPath)}`);
    this.entityPath = entityPath;

    const entityContent = fs.readFileSync(entityPath, 'utf8');
    const packageMatch = entityContent.match(/package\s+([\w.]+);/);
    if (!packageMatch) {
      this.log.error('Package name not found in entity file');
      process.exit(1);
    }

    const entityPackage = packageMatch[1];
    const modulePath = this.entityConfig?.modulePath;

    this.props = {
      ...this.props,
      entityName,
      modulePath,
      targetPaths: this.targetPaths,
      entityPackage,
    };
  }

  async writing() {
    const entityContent = this.fs.read(this.entityPath);
    const entityComment = extractTableComment(entityContent);
    const { fields, idType, embeddedIdField, embeddedIdFields } = analyzeEntity(entityContent);

    // 추가: EmbeddedId 관련 변수 설정
    const hasEmbeddedId = Boolean(embeddedIdField);
    let embeddedIdPropertyNames = [];
    let embeddedIdType = null;

    if (hasEmbeddedId) {
      this.log(`\n✅ Detected EmbeddedId: ${embeddedIdField} of type ${idType}`);
      embeddedIdType = idType;

      if (embeddedIdFields && embeddedIdFields.length > 0) {
        embeddedIdPropertyNames = embeddedIdFields.map(field => field.name);
        this.log(`   With properties: ${embeddedIdPropertyNames.join(', ')}`);
      }
    }

    // 필드 검색 옵션 준비
    const { embeddedIdSearchFields, entitySearchFields } = this.prepareSearchFields(
      fields,
      embeddedIdFields,
      embeddedIdPropertyNames,
      idType,
      embeddedIdField,
      embeddedIdType,
      hasEmbeddedId
    );

    // 템플릿 변수 설정
    const templateVars = {
      ...this.props,
      entityComment,
      fields,
      idType,
      entityFields: fields,
      // EmbeddedId 관련 변수
      hasEmbeddedId,
      embeddedIdField,
      embeddedIdType,
      embeddedIdPropertyNames,
      embeddedIdPropertyName1: embeddedIdPropertyNames[0] || 'propertyName1',
      embeddedIdPropertyName2: embeddedIdPropertyNames[1] || 'propertyName2',
      otherEntityField: fields.find(f => !f.id && f.name !== embeddedIdField)?.name || 'someField',
      embeddedIdFields,
      // 검색 필드 데이터
      embeddedIdSearchFields,
      entitySearchFields,
      // 가공된 변수
      embeddedFieldCapitalized: embeddedIdField
        ? embeddedIdField + capitalizeFirstLetter(embeddedIdPropertyNames[0] || 'propertyName1')
        : '',
      embeddedField2Capitalized: embeddedIdField
        ? embeddedIdField + capitalizeFirstLetter(embeddedIdPropertyNames[1] || 'propertyName2')
        : '',
      otherFieldCapitalized: capitalizeFirstLetter(
        fields.find(f => !f.id && f.name !== embeddedIdField)?.name || 'someField'
      ),
      propType1: this.getPropertyType(embeddedIdFields, embeddedIdPropertyNames[0]),
      propType2: this.getPropertyType(embeddedIdFields, embeddedIdPropertyNames[1]),
      importsArray: this.getNeededImports(embeddedIdFields),
      ymlConfig: {
        ...this.entityConfig,
        thymeleafBaseDir: this.entityConfig?.thymeleafBaseDir || '',
      },
      _: lodash,
      generatedAt: (() => {
        const d = new Date(Date.now() + 9 * 60 * 60 * 1000); // KST
        return d.toISOString().replace('Z', '+09:00');
      })(),
    };

    this.log('\n📦 Files to generate:');

    for (const file of this.filesToGenerate) {
      const targetPath = file.path;
      const templatePath = file.template;
      const exists = fs.existsSync(targetPath);

      if (exists && !this.options.force) {
        this.log(
          `   ⚠️  [SKIP] ${path.relative(process.cwd(), targetPath)} (already exists, use --force to overwrite)`
        );
        continue;
      }

      this.log(`   ✅ ${path.relative(process.cwd(), targetPath)}`);

      // Ensure the directory exists
      const targetDir = path.dirname(targetPath);
      if (!fs.existsSync(targetDir)) {
        fs.mkdirSync(targetDir, { recursive: true });
      }

      // Read template and render with EJS
      try {
        const template = fs.readFileSync(templatePath, 'utf8');
        let rendered = ejs.render(template, templateVars, {
          escape: str => str,
        });

        // 연속된 빈 줄을 하나로 정리
        rendered = rendered.replace(/\n\s*\n\s*\n/g, '\n\n');

        fs.writeFileSync(targetPath, rendered);
      } catch (error) {
        this.log(`   ❌ Error generating ${path.relative(process.cwd(), targetPath)}: ${error.message}`);
      }
    }

    this.log('\n✅ Generation completed successfully.');
  }

  /**
   * 필드 타입을 가져옵니다.
   * @param {Array} embeddedIdFields 복합키 필드 정보
   * @param {String} propertyName 속성명
   * @returns {String} 타입
   */
  getPropertyType(embeddedIdFields, propertyName) {
    if (!embeddedIdFields || !embeddedIdFields.length || !propertyName) return 'String';

    const field = embeddedIdFields.find(f => f.name === propertyName);
    return field ? field.type : 'String';
  }

  /**
   * 필요한 임포트를 가져옵니다.
   * @param {Array} embeddedIdFields 복합키 필드 정보
   * @returns {Array} 임포트 배열
   */
  getNeededImports(embeddedIdFields) {
    if (!embeddedIdFields || !Array.isArray(embeddedIdFields) || !embeddedIdFields.length) {
      return [];
    }

    const neededImports = new Set();

    for (const field of embeddedIdFields) {
      if (!field || !field.name || !field.type) continue;

      const typeName = field.type.trim();

      if (['LocalDateTime', 'LocalDate', 'LocalTime', 'ZonedDateTime', 'Instant'].includes(typeName)) {
        neededImports.add(`java.time.${typeName}`);
      } else if (typeName === 'Date') {
        neededImports.add('java.util.Date');
      } else if (typeName.includes('List') || typeName.includes('Set') || typeName.includes('Map')) {
        neededImports.add(`java.util.${typeName.split('<')[0]}`);
      } else if (typeName.includes('.')) {
        neededImports.add(typeName);
      }
    }

    return Array.from(neededImports);
  }

  /**
   * 연산자 문자열을 생성합니다.
   * @param {Array} searchOperators 연산자 배열
   * @returns {String} 변환된 연산자 문자열
   */
  getOperatorsString(searchOperators) {
    if (!searchOperators || !Array.isArray(searchOperators) || !searchOperators.length) {
      return 'EQUALS';
    }

    const operatorStrings = [];
    for (const op of searchOperators) {
      let result;
      switch (op) {
        case 'equals':
          result = 'EQUALS';
          break;
        case 'contains':
          result = 'CONTAINS';
          break;
        case 'startsWith':
          result = 'STARTS_WITH';
          break;
        case 'endsWith':
          result = 'ENDS_WITH';
          break;
        case 'greaterThan':
          result = 'GREATER_THAN';
          break;
        case 'lessThan':
          result = 'LESS_THAN';
          break;
        case 'between':
          result = 'BETWEEN';
          break;
        default:
          result = 'EQUALS';
          break;
      }

      operatorStrings.push(result);
    }

    return operatorStrings.join(', ');
  }

  /**
   * 검색 필드 데이터를 준비합니다.
   * @param {Array} fields 엔티티 필드
   * @param {Array} embeddedIdFields 복합키 필드 정보
   * @param {Array} embeddedIdPropertyNames 복합키 프로퍼티명
   * @param {String} idType ID 타입
   * @param {String} embeddedIdField 복합키 필드명
   * @param {String} embeddedIdType 복합키 타입
   * @param {Boolean} hasEmbeddedId 복합키 여부
   * @returns {Object} 검색 필드 데이터
   */
  prepareSearchFields(
    fields,
    embeddedIdFields,
    embeddedIdPropertyNames,
    idType,
    embeddedIdField,
    embeddedIdType,
    hasEmbeddedId
  ) {
    // 복합키 검색 필드 준비
    const embeddedIdSearchFields = this.prepareEmbeddedIdSearchFields(
      fields,
      embeddedIdFields,
      embeddedIdPropertyNames,
      hasEmbeddedId
    );

    // 일반 엔티티 필드 검색 옵션 준비
    const entitySearchFields = this.prepareEntitySearchFields(fields);

    return { embeddedIdSearchFields, entitySearchFields };
  }

  /**
   * 복합키 필드의 검색 옵션을 준비합니다.
   * @param {Array} fields 엔티티 필드
   * @param {Array} embeddedIdFields 복합키 필드 정보
   * @param {Array} embeddedIdPropertyNames 복합키 프로퍼티명
   * @param {Boolean} hasEmbeddedId 복합키 여부
   * @returns {Array} 복합키 검색 필드 배열
   */
  prepareEmbeddedIdSearchFields(fields, embeddedIdFields, embeddedIdPropertyNames, hasEmbeddedId) {
    if (!hasEmbeddedId) return [];

    let result = [];

    // 유효성 검사 추가
    const validFields = Array.isArray(fields) ? fields : [];
    const validEmbeddedIdFields = Array.isArray(embeddedIdFields) ? embeddedIdFields : [];
    const validPropertyNames = Array.isArray(embeddedIdPropertyNames) ? embeddedIdPropertyNames : [];

    // 1. embeddedFields에서 필드 정보 추출
    const embeddedFields = validFields.filter(field => field && (field.embeddedField || field.isEmbeddedField));

    if (embeddedFields.length > 0) {
      result = embeddedFields.map(field => this.createSearchField(field));
    }
    // 2. embeddedIdFields에서 필드 정보 추출
    else if (validEmbeddedIdFields.length > 0) {
      result = validEmbeddedIdFields.map(field => this.createSearchField(field, true));
    }
    // 3. embeddedIdPropertyNames에서 필드 정보 추출
    else if (validPropertyNames.length > 0) {
      result = [];
      for (const propertyName of validPropertyNames) {
        const field = validEmbeddedIdFields.find(f => f && f.name === propertyName);
        const type = field ? field.type : this.getPropertyType(validEmbeddedIdFields, propertyName);
        result.push(this.createSearchField({ name: propertyName, type, comment: propertyName }, true));
      }
    }

    return result;
  }

  /**
   * 일반 엔티티 필드의 검색 옵션을 준비합니다.
   * @param {Array} fields 엔티티 필드
   * @returns {Array} 필드 검색 옵션 배열
   */
  prepareEntitySearchFields(fields) {
    if (!fields || !Array.isArray(fields) || !fields.length) {
      return [];
    }

    const result = [];

    for (const field of fields) {
      if (!field) continue;

      const ymlField = this.entityConfig?.fields?.[field.name] || {};

      if (ymlField.searchOperators && Array.isArray(ymlField.searchOperators) && ymlField.searchOperators.length > 0) {
        result.push(this.createSearchField(field, false, ymlField.reference));
      }
    }

    return result;
  }

  /**
   * 검색 필드 객체를 생성합니다.
   * @param {Object} field 필드 정보
   * @param {Boolean} isEmbedded 복합키 필드 여부
   * @param {Object} reference 참조 정보
   * @returns {Object} 검색 필드 객체
   */
  createSearchField(field, isEmbedded = false, reference = null) {
    if (!field) return null;

    const fieldName = field.name;
    const ymlField = isEmbedded
      ? this.entityConfig?.fields?.[`${this.entityConfig.idField}.${fieldName}`] || {}
      : this.entityConfig?.fields?.[fieldName] || {};

    let outputFieldName = fieldName;
    let outputFieldType = field.type;
    let entityFieldPath;

    if (reference) {
      outputFieldName = reference.idField;
      outputFieldType = reference.idType;
      entityFieldPath = `${fieldName}.${reference.idField}`; // codeGroup.codeGroupId
    } else if (isEmbedded) {
      entityFieldPath = `id.${fieldName}`;
    }

    const searchOperators = ymlField.searchOperators || ['equals'];
    const operators = this.getOperatorsString(searchOperators);
    const fieldComment = field.comment ? field.comment.split(':')[0] : ymlField.comment || fieldName;

    return {
      name: outputFieldName,
      type: outputFieldType,
      comment: fieldComment,
      entityField: entityFieldPath,
      operators,
      sortable: ymlField.sortable || false,
    };
  }
}
