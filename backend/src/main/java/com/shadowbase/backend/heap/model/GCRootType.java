package com.shadowbase.backend.heap.model;

/**
 * Types of Garbage Collection (GC) Roots in a Java heap dump,
 * matching JVM / MAT / Jhat root classifications.
 */
public enum GCRootType {
    JNI_GLOBAL("JNI Global Reference"),
    JNI_LOCAL("JNI Local Reference"),
    JAVA_FRAME("Java Local Variable / Stack Frame"),
    NATIVE_STACK("Native Stack Frame"),
    STICKY_CLASS("System / Loaded Class"),
    THREAD_BLOCK("Thread Block / Synchronized"),
    MONITOR_USED("Active Object Monitor"),
    THREAD_OBJECT("Live Running Thread"),
    UNKNOWN("Unknown Root");

    private final String description;

    GCRootType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public static GCRootType fromHprofTag(int tag) {
        return switch (tag) {
            case 0x01 -> JNI_GLOBAL;
            case 0x02 -> JNI_LOCAL;
            case 0x03 -> JAVA_FRAME;
            case 0x04 -> NATIVE_STACK;
            case 0x05 -> STICKY_CLASS;
            case 0x06 -> THREAD_BLOCK;
            case 0x07 -> MONITOR_USED;
            case 0x08 -> THREAD_OBJECT;
            default -> UNKNOWN;
        };
    }
}
