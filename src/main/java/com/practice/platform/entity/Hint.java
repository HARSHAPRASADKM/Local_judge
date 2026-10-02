package com.practice.platform.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "hints")
public class Hint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private UserAccount user;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Problem problem;

    private int level;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String text;

    private Instant unlockedAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UserAccount getUser() { return user; }
    public void setUser(UserAccount user) { this.user = user; }
    public Problem getProblem() { return problem; }
    public void setProblem(Problem problem) { this.problem = problem; }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public Instant getUnlockedAt() { return unlockedAt; }
    public void setUnlockedAt(Instant unlockedAt) { this.unlockedAt = unlockedAt; }
}
