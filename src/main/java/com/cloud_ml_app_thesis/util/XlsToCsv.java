package com.cloud_ml_app_thesis.util;

import com.cloud_ml_app_thesis.exception.FileProcessingException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

public class XlsToCsv {
    private static final Logger logger = LoggerFactory.getLogger(XlsToCsv.class);

    /**
     * Converts an Excel file (.xls or .xlsx) to CSV format
     * @param excelInputStream Input stream of the Excel file
     * @param originalFileName Original file name (for logging)
     * @return Path to the temporary CSV file
     * @throws FileProcessingException if conversion fails
     */
    public static String convertExcelToCsv(InputStream excelInputStream, String originalFileName) {
        logger.info("📊 Converting Excel file to CSV: {}", originalFileName);
        File tempCsvFile = null;

        try {
            // Create temporary CSV file
            tempCsvFile = File.createTempFile("excel_converted_", ".csv");

            // Wrap in BufferedInputStream to support mark/reset for format detection
            BufferedInputStream bufferedStream = new BufferedInputStream(excelInputStream);

            try (Workbook workbook = WorkbookFactory.create(bufferedStream);
                 PrintWriter writer = new PrintWriter(new FileWriter(tempCsvFile))) {

                Sheet sheet = workbook.getSheetAt(0);
                logger.info("📄 Processing sheet: {} with {} rows", sheet.getSheetName(), sheet.getPhysicalNumberOfRows());

                // Determine the maximum number of columns across all rows
                int maxColumns = 0;
                for (Row row : sheet) {
                    if (row != null) {
                        int lastCell = row.getLastCellNum();
                        if (lastCell > maxColumns) {
                            maxColumns = lastCell;
                        }
                    }
                }

                if (maxColumns == 0) {
                    throw new IllegalStateException("Excel sheet is empty or has no columns");
                }
                logger.info("📊 Detected {} columns (max across all rows)", maxColumns);

                int rowCount = 0;
                for (Row row : sheet) {
                    for (int colIndex = 0; colIndex < maxColumns; colIndex++) {
                        if (colIndex > 0) {
                            writer.print(",");
                        }

                        Cell cell = row.getCell(colIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                        String cellValue = getCellValueAsString(cell);

                        // Always quote empty values to ensure they're recognized as a column
                        if (cellValue.isEmpty()) {
                            writer.print("\"\"");
                        } else if (cellValue.contains(",") || cellValue.contains("\"") || cellValue.contains("\n")) {
                            // Escape values containing commas or quotes
                            cellValue = "\"" + cellValue.replace("\"", "\"\"") + "\"";
                            writer.print(cellValue);
                        } else {
                            writer.print(cellValue);
                        }
                    }
                    writer.println();
                    rowCount++;
                }

                logger.info("✅ Excel to CSV conversion complete: {} rows x {} columns", rowCount, maxColumns);
            }

            return tempCsvFile.getAbsolutePath();

        } catch (Exception e) {
            // Clean up temp file on error
            if (tempCsvFile != null && tempCsvFile.exists()) {
                tempCsvFile.delete();
            }
            logger.error("❌ Failed to convert Excel to CSV: {}", e.getMessage());
            throw new FileProcessingException("Failed to convert Excel file to CSV format", e);
        }
    }

    /**
     * Extracts cell value as string, handling different cell types
     */
    private static String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    // Format numbers to avoid scientific notation
                    double numValue = cell.getNumericCellValue();
                    if (numValue == (long) numValue) {
                        return String.valueOf((long) numValue);
                    } else {
                        return String.valueOf(numValue);
                    }
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                // Evaluate formula and get cached value
                try {
                    return getCellValueAsString(cell.getCachedFormulaResultType(), cell);
                } catch (Exception e) {
                    return cell.getCellFormula();
                }
            case BLANK:
                return "";
            default:
                return "";
        }
    }

    /**
     * Helper method to get cached formula result
     */
    private static String getCellValueAsString(CellType cellType, Cell cell) {
        switch (cellType) {
            case STRING:
                return cell.getRichStringCellValue().getString();
            case NUMERIC:
                double numValue = cell.getNumericCellValue();
                if (numValue == (long) numValue) {
                    return String.valueOf((long) numValue);
                } else {
                    return String.valueOf(numValue);
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                return "";
        }
    }

    /**
     * Main method for standalone testing
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: java XlsToCsv <input.xls|input.xlsx> <output.csv>");
            System.exit(1);
        }

        try (InputStream in = new FileInputStream(args[0])) {
            String csvPath = convertExcelToCsv(in, args[0]);
            System.out.println("Converted to: " + csvPath);

            // If output path specified, copy to that location
            if (args.length >= 2) {
                Files.copy(Path.of(csvPath), Path.of(args[1]));
                System.out.println("Copied to: " + args[1]);
            }
        }
    }
}
