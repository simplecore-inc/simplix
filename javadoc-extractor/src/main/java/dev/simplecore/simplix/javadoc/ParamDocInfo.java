package dev.simplecore.simplix.javadoc;

import lombok.Data;

/**
 * Class to store Javadoc information for a method parameter.
 */
@Data
public class ParamDocInfo {
    private String name;
    private String type;
    private String comment;
}