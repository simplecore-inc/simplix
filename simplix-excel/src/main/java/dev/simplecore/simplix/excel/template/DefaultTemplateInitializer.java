/*
 * Copyright (c) 2025 SimpleCORE
 * Licensed under the SimpleCORE License 1.0 (see LICENSE)
 * Use allowed in own products. Redistribution or resale requires permission.
 */
package dev.simplecore.simplix.excel.template;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

@Slf4j
public class DefaultTemplateInitializer {
    
    private static final String DEFAULT_TEMPLATE_PATH = "templates/default-template.xlsx";
    
    public static void initializeDefaultTemplate() {
        File templateDir = new File("templates");
        if (!templateDir.exists()) {
            templateDir.mkdirs();
        }
        
        File templateFile = new File(DEFAULT_TEMPLATE_PATH);
        if (!templateFile.exists()) {
            try (Workbook workbook = new XSSFWorkbook()) {
                workbook.createSheet("Data");
                try (FileOutputStream fos = new FileOutputStream(templateFile)) {
                    workbook.write(fos);
                }
                log.info("Default template created at: {}", templateFile.getAbsolutePath());
            } catch (IOException e) {
                log.error("Failed to create default template", e);
            }
        }
    }
} 