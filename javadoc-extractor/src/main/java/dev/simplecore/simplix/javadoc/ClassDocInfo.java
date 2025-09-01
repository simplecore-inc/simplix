package dev.simplecore.simplix.javadoc;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * Class to store Javadoc information for a class.
 */
@Getter
@Setter
public class ClassDocInfo {
    private String name;
    private String qualifiedName;
    private String comment;
    private String packageName;
    private String modifiers;
    private boolean isInterface;
    private boolean isEnum;
    private boolean isAnnotation;
    private Map<String, List<String>> tags;
    private List<FieldDocInfo> fields;
    private List<MethodDocInfo> methods;
    
    // Explicit setters for boolean properties to avoid compilation issues
    public void setIsInterface(boolean isInterface) {
        this.isInterface = isInterface;
    }
    
    public void setIsEnum(boolean isEnum) {
        this.isEnum = isEnum;
    }
    
    public void setIsAnnotation(boolean isAnnotation) {
        this.isAnnotation = isAnnotation;
    }
}