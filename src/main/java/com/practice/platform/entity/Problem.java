package com.practice.platform.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "problems")
public class Problem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private UserAccount owner;

    @Column(nullable = false)
    private String title;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String statement;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String constraintsText;

    @Column(length = 20)
    private String difficulty;

    @Column(length = 400)
    private String topicTags;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String starterJava;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String starterPython;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String referenceJava;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String referencePython;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String originalPrompt;

    private boolean verified = true;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String postAcceptReview;

    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UserAccount getOwner() { return owner; }
    public void setOwner(UserAccount owner) { this.owner = owner; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getStatement() { return statement; }
    public void setStatement(String statement) { this.statement = statement; }
    public String getConstraintsText() { return constraintsText; }
    public void setConstraintsText(String constraintsText) { this.constraintsText = constraintsText; }
    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
    public String getTopicTags() { return topicTags; }
    public void setTopicTags(String topicTags) { this.topicTags = topicTags; }
    public String getStarterJava() { return starterJava; }
    public void setStarterJava(String starterJava) { this.starterJava = starterJava; }
    public String getStarterPython() { return starterPython; }
    public void setStarterPython(String starterPython) { this.starterPython = starterPython; }
    public String getReferenceJava() { return referenceJava; }
    public void setReferenceJava(String referenceJava) { this.referenceJava = referenceJava; }
    public String getReferencePython() { return referencePython; }
    public void setReferencePython(String referencePython) { this.referencePython = referencePython; }
    public String getOriginalPrompt() { return originalPrompt; }
    public void setOriginalPrompt(String originalPrompt) { this.originalPrompt = originalPrompt; }
    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }
    public String getPostAcceptReview() { return postAcceptReview; }
    public void setPostAcceptReview(String postAcceptReview) { this.postAcceptReview = postAcceptReview; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
