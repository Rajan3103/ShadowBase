package com.shadowbase.backend.heap.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Metadata descriptor for an object field.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HeapField {

    /**
     * Field name (e.g., "name", "next", "elements").
     */
    private String name;

    /**
     * HPROF type code (e.g., 2 for Object reference, 4 for boolean, 8 for byte, etc.).
     */
    private int typeCode;

    /**
     * Type name (e.g., "object", "int", "long", "boolean").
     */
    private String typeName;

    /**
     * True if the field is a reference pointing to another object ID.
     */
    private boolean reference;

    public static String getTypeNameFromCode(int typeCode) {
        return switch (typeCode) {
            case 2 -> "object";
            case 4 -> "boolean";
            case 5 -> "char";
            case 6 -> "float";
            case 7 -> "double";
            case 8 -> "byte";
            case 9 -> "short";
            case 10 -> "int";
            case 11 -> "long";
            default -> "unknown(" + typeCode + ")";
        };
    }

    public static int getTypeSize(int typeCode, int idSize) {
        return switch (typeCode) {
            case 2 -> idSize; // object reference
            case 4, 8 -> 1;  // boolean, byte
            case 5, 9 -> 2;  // char, short
            case 6, 10 -> 4; // float, int
            case 7, 11 -> 8; // double, long
            default -> idSize;
        };
    }
}
