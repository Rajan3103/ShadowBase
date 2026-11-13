package com.shadowbase.backend.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shadowbase.backend.Entity.User;

public  interface UserRepository extends JpaRepository{

Optional<User> findById(UUID id);

List<User> findByUserName(String userName);


}
