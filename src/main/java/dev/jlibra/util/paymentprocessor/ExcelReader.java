package dev.jlibra.util.paymentprocessor;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ExcelReader {

    public List<Map<String, String>> readExcel() throws IOException {
        ClassLoader classLoader = getClass().getClassLoader();
        List<Map<String, String>> maps = readExcelData(classLoader.getResourceAsStream("wintermute_employees.xlsx"));
        return maps.subList(1, maps.size());
    }

    private List<Map<String, String>> readExcelData(InputStream fFile) throws IOException {
        Workbook workbook = new XSSFWorkbook(fFile);
        Sheet sheet = workbook.getSheetAt(0);
        List<Map<String, String>> data = new ArrayList<>();
        for (Row row : sheet) {
            Map<String, String> rowMap = new HashMap<>();
            for (Cell cell : row) {
                String value = null;
                switch (cell.getCellTypeEnum()) {
                    case STRING: value = cell.getStringCellValue(); break;
                    case NUMERIC: value = String.valueOf(cell.getNumericCellValue()); break;
                    case BOOLEAN: value = String.valueOf(cell.getBooleanCellValue()); break;
                    case FORMULA: value = String.valueOf(cell.getCellFormula()); break;
                }
                rowMap.put(sheet.getRow(0).getCell(cell.getColumnIndex()).getStringCellValue(), value);
            }
            data.add(rowMap);
        }
        return data;
    }

}
