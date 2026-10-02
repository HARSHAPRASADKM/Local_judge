package com.practice.platform.repo;

import com.practice.platform.entity.Problem;
import com.practice.platform.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProblemRepository extends JpaRepository<Problem, Long> {
    List<Problem> findByOwnerOrderByCreatedAtDesc(UserAccount owner);

    @Query("select p from Problem p join fetch p.owner where p.id = :id")
    Optional<Problem> findWithOwnerById(@Param("id") Long id);

    @Query("select p.title from Problem p where p.owner = :owner order by p.createdAt desc")
    List<String> findTitlesByOwner(@Param("owner") UserAccount owner);
}
