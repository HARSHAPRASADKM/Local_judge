package com.practice.platform.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "notes", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "problem_id"}))
public class Note {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private UserAccount user;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Problem problem;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String content = "";

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UserAccount getUser() { return user; }
    public void setUser(UserAccount user) { this.user = user; }
    public Problem getProblem() { return problem; }
    public void setProblem(Problem problem) { this.problem = problem; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
