# generator-simplix
> SimpliX Generator - Spring Boot Code Generator

## Installation

First, install [Yeoman](http://yeoman.io) and generator-simplix using [npm](https://www.npmjs.com/) (we assume you have pre-installed [node.js](https://nodejs.org/)).

```bash
npm install -g yo
```

### Local Installation

1. Move to generator directory and install dependencies:
```bash
cd generator-simplix
npm install
```

2. Link the package locally:
```bash
npm link
```

Now you can use the generator in any directory:
```bash
yo simplix
```

## Available Commands

### 1. Generate Entity Config
First, generate configuration file for your entity:
```bash
yo simplix:config UserAccount
```
This will create `.simplix/entity/UserAccount.yml` file. You may need to modify some fields in the config file.

### 2. Entity CRUD Generator
After configuring the entity, generate Repository, Service, and Controller:
```bash
yo simplix:entity UserAccount
```

Options:
- `--force` Force overwrite existing files

## Project Structure
Your Spring Boot project should follow the standard Maven/Gradle structure:
```
src/
  main/
    java/
      com/example/
        entity/
          UserAccount.java
        repository/
        service/
        controller/
```
