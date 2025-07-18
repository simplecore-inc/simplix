package dev.simplecore.simplix.javadoc;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Class to store Javadoc information for a field.
 */
@Data
public class FieldDocInfo {
    private String name;
    private String comment;
    private String type;
    private String modifiers;
    private Map<String, List<String>> tags;
}