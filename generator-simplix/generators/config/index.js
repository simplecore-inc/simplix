import Generator from 'yeoman-generator';
import path from 'path';
import fs from 'graceful-fs';
import yaml from 'js-yaml';
import { findEntityFile } from '../utils/file-utils.js';
import { analyzeEntity } from '../utils/entity-analyzer.js';

// Helper function to find the most suitable name field
function findNameField(fields, idFieldName = 'id') {
  const nameFieldPatterns = ['title', 'name', 'detail', 'description', 'words'];
  for (const pattern of nameFieldPatterns) {
    const foundField = fields.find(
      field =>
        field.name.toLowerCase().includes(pattern) &&
        !field.isCollection && // Exclude collection types
        field.typeKind === 'basic' // Only allow basic types
    );
    if (foundField) {
      return foundField.name;
    }
  }

  // If no suitable name field is found, use the ID field
  return idFieldName;
}

export default class extends Generator {
  constructor(args, opts) {
    super(args, opts);

    this.argument('entityName', {
      type: String,
      required: true,
      description: 'Name of the entity to generate config file for',
    });

    this.option('force', {
      type: Boolean,
      default: false,
      description: 'Force overwrite existing config file',
    });
  }

  async writing() {
    const { entityName } = this.options;
    const srcPath = path.join(process.cwd(), 'src/main/java');
    const entityPath = findEntityFile(srcPath, entityName);

    if (!entityPath) {
      this.log('\n🚫 Error: Entity file not found');
      process.exit(1);
    }

    const entityContent = fs.readFileSync(entityPath, 'utf8');
    const { fields, embeddedIdFields, idField } = analyzeEntity(entityContent);

    // Find the ID field
    let idFieldName = idField || 'id';

    // Find the name field
    let nameField = findNameField(fields, idFieldName);

    // Extract module path from package
    const packageMatch = entityContent.match(/package\s+([\w.]+);/);
    let modulePath = '<__MODIFY__>';
    if (packageMatch) {
      const fullPackage = packageMatch[1];
      const domainIndex = fullPackage.indexOf('.domain.');
      if (domainIndex !== -1) {
        const pathAfterDomain = fullPackage.substring(domainIndex + '.domain.'.length);
        modulePath = pathAfterDomain
          .split('.')
          .filter(part => part !== 'entity')
          .join('/');
      }
    }

    const config = {
      entity: entityName,
      modulePath,
      idField: idFieldName,
      nameField,
      thymeleafBaseDir: entityName
        .replace(/([A-Z])/g, '/$1')
        .replace(/^\//, '')
        .toLowerCase(),
      defaultSortField: 'createdAt',
      defaultSortDirection: 'desc',
      fields: {},
    };

    // Handle embedded ID fields first
    if (embeddedIdFields) {
      embeddedIdFields.forEach(field => {
        const fieldConfig = {
          sortable: false,
          views: ['list', 'detail', 'edit', 'batchUpdate'],
        };

        // Set searchOperators based on field type
        if (field.typeKind === 'basic') {
          fieldConfig.searchOperators = ['equals'];
        }

        config.fields[`${field.parentField}.${field.name}`] = fieldConfig;
      });
    }

    // Handle regular fields
    // eslint-disable-next-line complexity
    fields.forEach(field => {
      // Skip fields that are part of embedded ID
      if (embeddedIdFields && embeddedIdFields.some(ef => ef.name === field.name)) {
        return;
      }

      // Skip the embeddedId field itself if it exists
      const embeddedIdFieldNames = Object.keys(config.fields)
        .filter(key => key.includes('.'))
        .map(key => key.split('.')[0]);

      if (embeddedIdFieldNames.includes(field.name)) {
        return;
      }

      const fieldConfig = {
        sortable: false,
        views:
          field.name === idFieldName
            ? ['list', 'detail', 'edit']
            : field.type === 'String' || /sort|order/i.test(field.name)
              ? ['list', 'detail', 'edit']
              : ['list', 'detail', 'edit', 'batchUpdate'],
      };

      // Set searchOperators based on field type
      if (field.typeKind === 'basic') {
        // ID fields only support equals operator
        if (field.name === idFieldName) {
          fieldConfig.searchOperators = ['equals'];
        } else {
          switch (field.type) {
            case 'String':
              fieldConfig.searchOperators = ['equals', 'contains'];
              break;
            case 'Integer':
            case 'Long':
            case 'Double':
            case 'BigDecimal':
              fieldConfig.searchOperators = ['equals', 'greaterThan', 'lessThan'];
              break;
            case 'ZonedDateTime':
            case 'OffsetDateTime':
            case 'LocalDateTime':
            case 'LocalDate':
              fieldConfig.searchOperators = ['between', 'greaterThan', 'lessThan'];
              break;
            case 'Boolean':
              fieldConfig.searchOperators = ['equals'];
              break;
            default:
              fieldConfig.searchOperators = ['equals'];
          }
        }
      }

      // Handle entity reference fields
      if (field.typeKind === 'entity') {
        // Reference fields only support equals operator
        fieldConfig.searchOperators = ['equals'];
        // Remove batchUpdate from views for reference fields
        fieldConfig.views = ['list', 'detail', 'edit'];
        fieldConfig.reference = {
          entity: field.actualType || field.type,
          idField: 'id',
          idType: 'Long',
          nameField: '<__MODIFY__>',
          multiple: field.isCollection,
        };

        // Find reference entity file
        const referenceEntityPath = findEntityFile(srcPath, field.actualType || field.type);

        if (referenceEntityPath) {
          const referenceEntityContent = fs.readFileSync(referenceEntityPath, 'utf8');
          const { idField: referenceIdField, fields: referenceFields } = analyzeEntity(referenceEntityContent);

          // Set reference entity's ID field and type
          const actualReferenceIdField = referenceIdField || 'id';
          if (actualReferenceIdField) {
            fieldConfig.reference.idField = actualReferenceIdField;
            const idFieldInfo = referenceFields.find(f => f.name === actualReferenceIdField);
            if (idFieldInfo) {
              fieldConfig.reference.idType = idFieldInfo.type;
            }
          }

          // Set reference entity's name field
          fieldConfig.reference.nameField = findNameField(referenceFields, actualReferenceIdField);
        }
      }

      config.fields[field.name] = fieldConfig;
    });

    const ymlContent = yaml
      .dump(config, {
        indent: 2,
        lineWidth: -1,
        quotingType: '"',
        flowStyle: false,
        styles: {
          '!!null': 'empty',
        },
      })
      .replace(/^entity:.*$/m, '$&\n')
      .replace(/^fields:$/m, '\nfields:\n')
      .replace(/^ {2}(\w+):$/gm, '\n  $1:')
      .replace(/^ {2}(\w+\.\w+):$/gm, '\n  $1:')
      .replace(/\n{3,}/g, '\n\n')
      .replace(/^(\s+)(views|searchOperators):\n\s+-\s+(.+(?:\n\s+-\s+.+)*)/gm, (match, indent, key, items) => {
        const values = items.split('\n').map(line => line.replace(/\s+-\s+/, '').trim());
        return `${indent}${key}: [${values.join(', ')}]`;
      });

    const configPath = this.destinationPath(`.simplix/entity/${entityName}.yml`);

    if (fs.existsSync(configPath) && !this.options.force) {
      this.log(`\n⚠️  Config file already exists: ${entityName}.yml`);
      this.log('Use --force to overwrite');
      return;
    }

    this.fs.write(configPath, ymlContent);
    this.log(`\n✅ Generated config file: ${entityName}.yml`);
  }
}
