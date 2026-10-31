package dev.simplecore.simplix.excel.style;

import javax.xml.namespace.QName;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.xmlbeans.XmlCursor;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTStylesheet;

/**
 * Gives a workbook's stylesheet the built-in {@code Normal} cell style.
 *
 * <p>POI writes the cell formats every cell points at but no {@code cellStyles} element, so the
 * file has no named default style. Excel tolerates that; other readers do not all: openpyxl
 * warns "Workbook contains no default style" and substitutes its own. The element names format
 * record 0 of {@code cellStyleXfs} as {@code Normal}, which is what Excel itself writes. It is
 * inserted through a cursor because the lite OOXML schema POI ships carries no
 * {@code CTCellStyles} type.
 */
public final class DefaultCellStyle {

    private static final String NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private static final QName CELL_STYLES = new QName(NS, "cellStyles");
    private static final QName CELL_STYLE = new QName(NS, "cellStyle");

    /** Elements that follow {@code cellStyles} in the stylesheet's schema order. */
    private static final String[] FOLLOWING = {"dxfs", "tableStyles", "colors", "extLst"};

    private DefaultCellStyle() {
    }

    /**
     * Adds the {@code Normal} cell style when the workbook has none. Call it after the last
     * style is created and before the workbook is written.
     *
     * @param workbook an XSSF or SXSSF workbook; any other kind is left as it is
     */
    public static void ensure(Workbook workbook) {
        XSSFWorkbook xssf;
        if (workbook instanceof SXSSFWorkbook streaming) {
            xssf = streaming.getXSSFWorkbook();
        } else if (workbook instanceof XSSFWorkbook plain) {
            xssf = plain;
        } else {
            return;
        }
        CTStylesheet stylesheet = xssf.getStylesSource().getCTStylesheet();
        try (XmlCursor cursor = stylesheet.newCursor()) {
            if (cursor.toChild(CELL_STYLES)) {
                return;
            }
            boolean positioned = false;
            for (String name : FOLLOWING) {
                if (cursor.toChild(new QName(NS, name))) {
                    positioned = true;
                    break;
                }
            }
            if (!positioned) {
                cursor.toEndToken();
            }
            cursor.beginElement(CELL_STYLES);
            cursor.insertAttributeWithValue("count", "1");
            cursor.beginElement(CELL_STYLE);
            cursor.insertAttributeWithValue("name", "Normal");
            cursor.insertAttributeWithValue("xfId", "0");
            cursor.insertAttributeWithValue("builtinId", "0");
        }
    }
}
