package com.practice.platform.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "users")
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 190)
    private String email;

    @Column(nullable = false, length = 80)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    private String suggestedTopic;

    private int currentStreak;
    private LocalDate lastPracticeDate;
    private int totalAccepted;
    private int totalAttempts;
    private int hintUsage;

    @Column(length = 2000)
    private String topicFailJson = "{}";

    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getSuggestedTopic() { return suggestedTopic; }
    public void setSuggestedTopic(String suggestedTopic) { this.suggestedTopic = suggestedTopic; }
    public int getCurrentStreak() { return currentStreak; }
    public void setCurrentStreak(int currentStreak) { this.currentStreak = currentStreak; }
    public LocalDate getLastPracticeDate() { return lastPracticeDate; }
    public void setLastPracticeDate(LocalDate lastPracticeDate) { this.lastPracticeDate = lastPracticeDate; }
    public int getTotalAccepted() { return totalAccepted; }
    public void setTotalAccepted(int totalAccepted) { this.totalAccepted = totalAccepted; }
    public int getTotalAttempts() { return totalAttempts; }
    public void setTotalAttempts(int totalAttempts) { this.totalAttempts = totalAttempts; }
    public int getHintUsage() { return hintUsage; }
    public void setHintUsage(int hintUsage) { this.hintUsage = hintUsage; }
    public String getTopicFailJson() { return topicFailJson; }
    public void setTopicFailJson(String topicFailJson) { this.topicFailJson = topicFailJson; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
