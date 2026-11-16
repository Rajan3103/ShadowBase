package com.shadowbase.model;

/**
 * Data model representing a database event payload transmitted over Kafka.
 * <p>
 * Encapsulates the operation type (e.g. INSERT, UPDATE, DELETE), the target table,
 * and the serialized payload data.
 */
public class ShadowBaseMessage {

    /**
     * Unique message sequence or transaction ID.
     */
    private int id;

    /**
     * Type of SQL operation: INSERT, UPDATE, or DELETE.
     */
    private String operation;

    /**
     * Target database table affected by the change.
     */
    private String table;

    /**
     * Row contents or payload data associated with the event.
     */
    private String data;

    public ShadowBaseMessage() {
    }

    public ShadowBaseMessage(int id, String operation, String table, String data) {
        this.id = id;
        this.operation = operation;
        this.table = table;
        this.data = data;
    }

    public int getId() {
        return id;
    }

    public String getOperation() {
        return operation;
    }

    public String getTable() {
        return table;
    }

    public String getData() {
        return data;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public void setTable(String table) {
        this.table = table;
    }

    public void setData(String data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "ShadowBaseMessage{" +
                "id=" + id +
                ", operation='" + operation + '\'' +
                ", table='" + table + '\'' +
                ", data='" + data + '\'' +
                '}';
    }
}


