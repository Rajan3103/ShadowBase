package com.shadowbase.backend.Entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "data_source")
public class DataSource {
    // this class holds the source database details
@Id
@GeneratedValue(strategy=GenerationType.UUID)
private UUID id;

@Column(name="connection_name",nullable=false)
private String connectionName;

@Column(name="host",nullable=false)
private String host;

@Column(name="port",nullable = false)
private Integer port;

@Column(name="database_name",nullable = false)
private String databaseName;

@Column(name="database_username",nullable = false)
private String databaseUsername;

@Column(name="credential_reference")
private String credentialReference;

@CreationTimestamp
@Column(nullable=false)
private LocalDateTime createdAt;

@UpdateTimestamp
private LocalDateTime updatedAt;

}
