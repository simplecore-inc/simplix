import Generator from 'yeoman-generator';
import chalk from 'chalk';

export default class extends Generator {
  initializing() {
    this.log('\n' + chalk.cyan('SimpliX Generator') + ' v0.0.1\n');

    this.log(chalk.bold('Workflow:'));
    this.log('  1. yo simplix:config <entityName>  First, generate the yml config file for your entity');
    this.log('  2. Edit .simplix/entity/<entityName>.yml file to match your requirements');
    this.log('  3. yo simplix:entity <entityName>  Generate CRUD files based on your modified yml config');
    this.log('');

    this.log(chalk.bold('Available Commands:'));
    this.log('  yo simplix:config <entityName>  Generate yml config file for entity');
    this.log('  yo simplix:entity <entityName>  Generate CRUD files for entity');
    this.log('  yo simplix:entity <entityName> --force  Generate CRUD files and overwrite existing files');
    this.log('');

    this.log(chalk.bold('Examples:'));
    this.log('  yo simplix:config UserAccount');
    this.log('  # Edit .simplix/entity/UserAccount.yml as needed');
    this.log('  yo simplix:entity UserAccount\n');
  }

  writing() {
    // Create .simplix folder structure
    this.fs.write(this.destinationPath('.simplix/entity/.gitkeep'), '');
  }
}
