package dev.simplecore.simplix.email.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents an email address with optional display name.
 * <p>
 * Example: "John Doe" &lt;john.doe@example.com&gt;
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailAddress {

    /**
     * Email address (required).
     */
    @NotBlank(message = "Email address is required")
    @Email(message = "Invalid email address format")
    private String address;

    /**
     * Display name (optional).
     * If provided, email will be formatted as "Name" &lt;address&gt;
     */
    private String name;

    /**
     * Create EmailAddress from address string only.
     *
     * @param address email address
     * @return EmailAddress instance
     */
    public static EmailAddress of(String address) {
        return EmailAddress.builder()
                .address(address)
                .build();
    }

    /**
     * Create EmailAddress with name and address.
     *
     * @param name display name
     * @param address email address
     * @return EmailAddress instance
     */
    public static EmailAddress of(String name, String address) {
        return EmailAddress.builder()
                .name(name)
                .address(address)
                .build();
    }

    /**
     * Format address for email header.
     *
     * @return formatted address string
     */
    public String toFormattedString() {
        if (name == null || name.isBlank()) {
            return address;
        }
        return String.format("\"%s\" <%s>", name, address);
    }

    /**
     * Mask email address for logging (privacy protection).
     * <p>
     * Example: john.doe@example.com -&gt; j***@example.com
     *
     * @return masked email address
     */
    public String toMaskedString() {
        if (address == null || !address.contains("@")) {
            return "***";
        }
        String[] parts = address.split("@");
        if (parts.length != 2 || parts[0].isEmpty()) {
            return "***";
        }
        return parts[0].charAt(0) + "***@" + parts[1];
    }
}
