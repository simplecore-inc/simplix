package dev.simplecore.simplix.javadoc;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Class to store Javadoc information for a method.
 */
@Data
public class MethodDocInfo {
    private String name;
    private String comment;
    private String returnType;
    private String returnComment;
    private String modifiers;
    private List<ParamDocInfo> parameters;
    private Map<String, List<String>> tags;
}