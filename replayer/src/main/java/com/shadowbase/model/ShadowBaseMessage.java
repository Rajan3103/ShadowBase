package com.shadowbase.model;

public class ShadowBaseMessage {

        private int id;
        private String operation;
        private String table;
        private String data;

        public ShadowBaseMessage(){

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

