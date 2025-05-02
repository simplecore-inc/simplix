package dev.simplecore.simplix.demo.web.common.user.excel;

import dev.simplecore.simplix.demo.domain.common.user.entity.UserAccount;
import dev.simplecore.simplix.demo.domain.common.user.entity.UserOrganization;
import dev.simplecore.simplix.demo.domain.common.user.entity.UserRole;
import dev.simplecore.simplix.excel.annotation.ExcelColumn;
import lombok.Data;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;

import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

/**
 * UserAccount Excel Export DTO
 * Defines the Excel export format using ExcelColumn annotations.
 */
@Data
public class UserAccountListExcel {

    @ExcelColumn(name = "사용자 ID", bold = true)
    private String id;

    @ExcelColumn(name = "로그인 계정", bold = true)
    private String username;

    @ExcelColumn(name = "이름")
    private String realName;

    @ExcelColumn(name = "이메일", width = 25)
    private String email;

    @ExcelColumn(name = "휴대전화", alignment = HorizontalAlignment.CENTER)
    private String mobilePhone;

    @ExcelColumn(name = "계정상태", backgroundColor = IndexedColors.YELLOW)
    private String enabled;

    @ExcelColumn(name = "직급")
    private String positionName;

    @ExcelColumn(name = "직급코드")
    private String positionCode;

    @ExcelColumn(name = "직급설명", width = 25, wrapText = true)
    private String positionDescription;

    @ExcelColumn(name = "권한", width = 20)
    private String roleNames;

    @ExcelColumn(name = "소속조직", width = 30, wrapText = true)
    private String organizationNames;

    @ExcelColumn(name = "등록일시", alignment = HorizontalAlignment.CENTER)
    private String createdAt;

    /**
     * Creates an Excel DTO from UserAccountListDTO
     * 
     * @param userAccount The DTO to convert
     * @return Excel export DTO
     */
    public static UserAccountListExcel from(UserAccount userAccount) {
        if (userAccount == null) {
            return null;
        }
        
        UserAccountListExcel excel = new UserAccountListExcel();
        excel.setId(userAccount.getId());
        excel.setUsername(userAccount.getUsername());
        excel.setRealName(userAccount.getRealName());
        excel.setEmail(userAccount.getEmail());
        
        // Account activation status
        if (userAccount.getEnabled() != null) {
            excel.setEnabled(userAccount.getEnabled() ? "Y" : "N");
        }
        
        // Creation date/time
        if (userAccount.getCreatedAt() != null) {
            excel.setCreatedAt(userAccount.getCreatedAt().format(
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        }
        
        // Format mobile phone number
        excel.setMobilePhone(formatPhoneNumber(userAccount.getMobilePhone()));
        
        // Position information
        if (userAccount.getPosition() != null) {
            excel.setPositionName(userAccount.getPosition().getName());
            excel.setPositionCode(userAccount.getPosition().getItemOrder());
            excel.setPositionDescription(userAccount.getPosition().getDescription());
        }
        
        // Role information
        excel.setRoleNames(formatJoinWithLimit(
                userAccount.getRoles(), 
                UserRole::getName, 
                3, 
                ", ", 
                "... and %d more"));
        
        // Organization information
        excel.setOrganizationNames(formatJoinWithLimit(
                userAccount.getOrganizations(), 
                UserOrganization::getName, 
                5, 
                "\n", 
                "\n... and %d more organizations"));
        
        return excel;
    }
    
    /**
     * Format mobile phone number with hyphens
     * 
     * @param phoneNumber Original phone number
     * @return Formatted phone number with hyphens, or the original if formatting is not possible
     */
    private static String formatPhoneNumber(String phoneNumber) {
        // Return as is if null, empty, or already contains hyphens
        if (phoneNumber == null || phoneNumber.isEmpty() || phoneNumber.contains("-")) {
            return phoneNumber;
        }
        
        // Extract digits only
        String digits = phoneNumber.replaceAll("\\D", "");
        
        // Format mobile phone number (11 digits: 010-1234-5678 format, 10 digits: 010-123-4567 format)
        if (digits.length() == 11) {
            return String.format("%s-%s-%s", 
                    digits.substring(0, 3), 
                    digits.substring(3, 7), 
                    digits.substring(7));
        } else if (digits.length() == 10) {
            return String.format("%s-%s-%s", 
                    digits.substring(0, 3), 
                    digits.substring(3, 6), 
                    digits.substring(6));
        }
        
        // Return original if formatting is not possible
        return phoneNumber;
    }
    
    /**
     * Format a collection by displaying a limited number of items and summarizing the rest
     * 
     * @param <T> Type of items in the collection
     * @param items Collection of items to format
     * @param mapper Function to extract a string from each item
     * @param limit Maximum number of items to display
     * @param delimiter String to separate displayed items
     * @param moreFormat Format string for additional items summary (e.g., "... and %d more")
     * @return Formatted string representation
     */
    private static <T> String formatJoinWithLimit(
            java.util.Collection<T> items, 
            java.util.function.Function<T, String> mapper,
            int limit, 
            String delimiter, 
            String moreFormat) {
        
        if (items == null || items.isEmpty()) {
            return "";
        }
        
        String result = items.stream()
                .map(mapper)
                .limit(limit)
                .collect(Collectors.joining(delimiter));
        
        if (items.size() > limit) {
            result += String.format(moreFormat, items.size() - limit);
        }
        
        return result;
    }
}