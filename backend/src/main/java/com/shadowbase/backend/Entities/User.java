package com.shadowbase.backend.Entities;

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

@Entity
@Data
@Table(name = "users")
public class User {
    //basic user fields have introduced and furthur fields will be added based on
    // requirements

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "email_id",nullable = false,unique = true)
    private String emailId;

    @Column(name = "password",nullable  = false)
    private String password;

    @CreationTimestamp
    @Column(nullable = false)
    private LocalDateTime createAt;

    @UpdateTimestamp
    @Column(nullable = true)
    private LocalDateTime updatedAt;
}
