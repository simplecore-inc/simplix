import Generator from 'yeoman-generator';
import path from 'path';
import fs from 'graceful-fs';
import yaml from 'js-yaml';
import { findEntityFile } from '../utils/file-utils.js';
import { analyzeEntity } from '../utils/entity-analyzer.js';

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
    const { fields, embeddedIdFields, embeddedIdField } = analyzeEntity(entityContent);

    const config = {
      entity: entityName,
      modulePath: '<__MODIFY__>',
      idField: 'id',
      nameField: '<__MODIFY__>',
      thymeleafBaseDir: entityName
        .replace(/([A-Z])/g, '/$1')
        .replace(/^\//, '')
        .toLowerCase(),
      defaultSortField: '<__MODIFY__>',
      defaultSortDirection: 'asc',
      fields: {},
    };

    // Handle embedded ID fields first
    if (embeddedIdFields) {
      // If we have embedded ID fields, set the idField to the embedded ID field name
      config.idField = embeddedIdField;

      embeddedIdFields.forEach(field => {
        const fieldConfig = {
          sortable: false,
          views: ['list', 'detail', 'edit', 'batchUpdate'],
        };

        // Set searchOperators based on field type
        if (field.typeKind === 'basic') {
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

        config.fields[`${field.parentField}.${field.name}`] = fieldConfig;
      });
    }

    // Handle regular fields
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
        views: ['list', 'detail', 'edit', 'batchUpdate'],
      };

      // Set searchOperators based on field type
      if (field.typeKind === 'basic') {
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

      // Handle entity reference fields
      if (field.typeKind === 'entity') {
        // Reference fields only support equals operator
        fieldConfig.searchOperators = ['equals'];
        fieldConfig.reference = {
          entity: field.type,
          idField: '<__MODIFY__>',
          idType: '<__MODIFY__>',
          nameField: '<__MODIFY__>',
          multiple: field.isCollection,
        };
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
