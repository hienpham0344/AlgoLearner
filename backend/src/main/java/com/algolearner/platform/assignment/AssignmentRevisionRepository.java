package com.algolearner.platform.assignment;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssignmentRevisionRepository extends JpaRepository<AssignmentRevisionEntity, UUID> { }
