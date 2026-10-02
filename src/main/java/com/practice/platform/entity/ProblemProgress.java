package com.practice.platform.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "problem_progress", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "problem_id"}))
public class ProblemProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private UserAccount user;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Problem problem;

    private int consecutiveFailedSubmits;
    private boolean accepted;
    private int hintsUnlocked;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String lastCode;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String previousCode;

    @Column(length = 20)
    private String lastLanguage;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UserAccount getUser() { return user; }
    public void setUser(UserAccount user) { this.user = user; }
    public Problem getProblem() { return problem; }
    public void setProblem(Problem problem) { this.problem = problem; }
    public int getConsecutiveFailedSubmits() { return consecutiveFailedSubmits; }
    public void setConsecutiveFailedSubmits(int consecutiveFailedSubmits) { this.consecutiveFailedSubmits = consecutiveFailedSubmits; }
    public boolean isAccepted() { return accepted; }
    public void setAccepted(boolean accepted) { this.accepted = accepted; }
    public int getHintsUnlocked() { return hintsUnlocked; }
    public void setHintsUnlocked(int hintsUnlocked) { this.hintsUnlocked = hintsUnlocked; }
    public String getLastCode() { return lastCode; }
    public void setLastCode(String lastCode) { this.lastCode = lastCode; }
    public String getPreviousCode() { return previousCode; }
    public void setPreviousCode(String previousCode) { this.previousCode = previousCode; }
    public String getLastLanguage() { return lastLanguage; }
    public void setLastLanguage(String lastLanguage) { this.lastLanguage = lastLanguage; }
}
