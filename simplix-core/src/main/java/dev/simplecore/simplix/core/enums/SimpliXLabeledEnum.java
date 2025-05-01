package dev.simplecore.simplix.core.enums;

import com.fasterxml.jackson.annotation.JsonFormat;

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public interface SimpliXLabeledEnum {
    String name();

    String getLabel();

}