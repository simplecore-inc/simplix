package dev.simplecore.simplix.javadoc;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Class to export Javadoc information to Excel.
 */
public class JavadocExcelExporter {

    /**
     * Export Javadoc information to Excel.
     *
     * @param classDocInfos list of ClassDocInfo objects
     * @param outputPath path to output Excel file
     * @throws IOException if an I/O error occurs
     */
    public void exportToExcel(List<ClassDocInfo> classDocInfos, String outputPath) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            // Create styles
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle defaultStyle = createDefaultStyle(workbook);
            
            // Create classes sheet
            Sheet classesSheet = workbook.createSheet("Classes");
            createClassesSheet(classesSheet, classDocInfos, headerStyle, defaultStyle);
            
            // Create methods sheet
            Sheet methodsSheet = workbook.createSheet("Methods");
            createMethodsSheet(methodsSheet, classDocInfos, headerStyle, defaultStyle);
            
            // Create fields sheet
            Sheet fieldsSheet = workbook.createSheet("Fields");
            createFieldsSheet(fieldsSheet, classDocInfos, headerStyle, defaultStyle);
            
            // Auto size columns for all sheets
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                for (int j = 0; j < 10; j++) {
                    sheet.autoSizeColumn(j);
                }
            }
            
            // Write to file
            try (FileOutputStream fileOut = new FileOutputStream(outputPath)) {
                workbook.write(fileOut);
            }
        }
    }
    
    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
    
    private CellStyle createDefaultStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setWrapText(true);
        return style;
    }
    
    private void createClassesSheet(Sheet sheet, List<ClassDocInfo> classDocInfos, CellStyle headerStyle, CellStyle defaultStyle) {
        // Create header row
        Row headerRow = sheet.createRow(0);
        String[] headers = {"Package", "Class Name", "Type", "Modifiers", "Description", "Tags"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        
        // Create data rows
        int rowNum = 1;
        for (ClassDocInfo classDocInfo : classDocInfos) {
            Row row = sheet.createRow(rowNum++);
            
            Cell packageCell = row.createCell(0);
            packageCell.setCellValue(classDocInfo.getPackageName());
            packageCell.setCellStyle(defaultStyle);
            
            Cell nameCell = row.createCell(1);
            nameCell.setCellValue(classDocInfo.getName());
            nameCell.setCellStyle(defaultStyle);
            
            Cell typeCell = row.createCell(2);
            String type = "Class";
            if (classDocInfo.isInterface()) type = "Interface";
            else if (classDocInfo.isEnum()) type = "Enum";
            else if (classDocInfo.isAnnotation()) type = "Annotation";
            typeCell.setCellValue(type);
            typeCell.setCellStyle(defaultStyle);
            
            Cell modifiersCell = row.createCell(3);
            modifiersCell.setCellValue(classDocInfo.getModifiers());
            modifiersCell.setCellStyle(defaultStyle);
            
            Cell descriptionCell = row.createCell(4);
            descriptionCell.setCellValue(classDocInfo.getComment());
            descriptionCell.setCellStyle(defaultStyle);
            
            Cell tagsCell = row.createCell(5);
            tagsCell.setCellValue(formatTags(classDocInfo.getTags()));
            tagsCell.setCellStyle(defaultStyle);
        }
    }
    
    private void createMethodsSheet(Sheet sheet, List<ClassDocInfo> classDocInfos, CellStyle headerStyle, CellStyle defaultStyle) {
        // Create header row
        Row headerRow = sheet.createRow(0);
        String[] headers = {"Class", "Method Name", "Return Type", "Parameters", "Modifiers", "Description", "Return Description", "Tags"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        
        // Create data rows
        int rowNum = 1;
        for (ClassDocInfo classDocInfo : classDocInfos) {
            for (MethodDocInfo methodDocInfo : classDocInfo.getMethods()) {
                Row row = sheet.createRow(rowNum++);
                
                Cell classCell = row.createCell(0);
                classCell.setCellValue(classDocInfo.getQualifiedName());
                classCell.setCellStyle(defaultStyle);
                
                Cell nameCell = row.createCell(1);
                nameCell.setCellValue(methodDocInfo.getName());
                nameCell.setCellStyle(defaultStyle);
                
                Cell returnTypeCell = row.createCell(2);
                returnTypeCell.setCellValue(methodDocInfo.getReturnType());
                returnTypeCell.setCellStyle(defaultStyle);
                
                Cell parametersCell = row.createCell(3);
                parametersCell.setCellValue(formatParameters(methodDocInfo.getParameters()));
                parametersCell.setCellStyle(defaultStyle);
                
                Cell modifiersCell = row.createCell(4);
                modifiersCell.setCellValue(methodDocInfo.getModifiers());
                modifiersCell.setCellStyle(defaultStyle);
                
                Cell descriptionCell = row.createCell(5);
                descriptionCell.setCellValue(methodDocInfo.getComment());
                descriptionCell.setCellStyle(defaultStyle);
                
                Cell returnDescCell = row.createCell(6);
                returnDescCell.setCellValue(methodDocInfo.getReturnComment());
                returnDescCell.setCellStyle(defaultStyle);
                
                Cell tagsCell = row.createCell(7);
                tagsCell.setCellValue(formatTags(methodDocInfo.getTags()));
                tagsCell.setCellStyle(defaultStyle);
            }
        }
    }
    
    private void createFieldsSheet(Sheet sheet, List<ClassDocInfo> classDocInfos, CellStyle headerStyle, CellStyle defaultStyle) {
        // Create header row
        Row headerRow = sheet.createRow(0);
        String[] headers = {"Class", "Field Name", "Type", "Modifiers", "Description", "Tags"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        
        // Create data rows
        int rowNum = 1;
        for (ClassDocInfo classDocInfo : classDocInfos) {
            for (FieldDocInfo fieldDocInfo : classDocInfo.getFields()) {
                Row row = sheet.createRow(rowNum++);
                
                Cell classCell = row.createCell(0);
                classCell.setCellValue(classDocInfo.getQualifiedName());
                classCell.setCellStyle(defaultStyle);
                
                Cell nameCell = row.createCell(1);
                nameCell.setCellValue(fieldDocInfo.getName());
                nameCell.setCellStyle(defaultStyle);
                
                Cell typeCell = row.createCell(2);
                typeCell.setCellValue(fieldDocInfo.getType());
                typeCell.setCellStyle(defaultStyle);
                
                Cell modifiersCell = row.createCell(3);
                modifiersCell.setCellValue(fieldDocInfo.getModifiers());
                modifiersCell.setCellStyle(defaultStyle);
                
                Cell descriptionCell = row.createCell(4);
                descriptionCell.setCellValue(fieldDocInfo.getComment());
                descriptionCell.setCellStyle(defaultStyle);
                
                Cell tagsCell = row.createCell(5);
                tagsCell.setCellValue(formatTags(fieldDocInfo.getTags()));
                tagsCell.setCellStyle(defaultStyle);
            }
        }
    }
    
    private String formatParameters(List<ParamDocInfo> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        for (ParamDocInfo param : parameters) {
            sb.append(param.getType()).append(" ").append(param.getName());
            if (param.getComment() != null && !param.getComment().isEmpty()) {
                sb.append(" - ").append(param.getComment());
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }
    
    private String formatTags(Map<String, List<String>> tags) {
        if (tags == null || tags.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<String>> entry : tags.entrySet()) {
            for (String value : entry.getValue()) {
                sb.append(entry.getKey()).append(": ").append(value).append("\n");
            }
        }
        return sb.toString().trim();
    }
}