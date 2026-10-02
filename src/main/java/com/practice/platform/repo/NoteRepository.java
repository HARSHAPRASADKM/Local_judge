package com.practice.platform.repo;

import com.practice.platform.entity.Note;
import com.practice.platform.entity.Problem;
import com.practice.platform.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NoteRepository extends JpaRepository<Note, Long> {
    Optional<Note> findByUserAndProblem(UserAccount user, Problem problem);
}
