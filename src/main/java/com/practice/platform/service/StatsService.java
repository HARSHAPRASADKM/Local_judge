package com.practice.platform.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.practice.platform.entity.Attempt;
import com.practice.platform.entity.UserAccount;
import com.practice.platform.repo.AttemptRepository;
import com.practice.platform.repo.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class StatsService {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MM-dd");

    private final UserAccountRepository users;
    private final AttemptRepository attempts;
    private final ObjectMapper mapper;

    public StatsService(UserAccountRepository users, AttemptRepository attempts, ObjectMapper mapper) {
        this.users = users;
        this.attempts = attempts;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> lastSevenDays(UserAccount user) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        Map<LocalDate, int[]> buckets = new HashMap<>();
        Instant since = Instant.now().minus(Duration.ofDays(7));
        for (Attempt a : attempts.findByUserAndCreatedAtAfterOrderByCreatedAtAsc(user, since)) {
            LocalDate day = a.getCreatedAt().atZone(zone).toLocalDate();
            if (day.isBefore(today.minusDays(6))) {
                continue;
            }
            int[] c = buckets.computeIfAbsent(day, k -> new int[2]);
            c[0]++;
            if (a.isAccepted()) {
                c[1]++;
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            int[] c = buckets.getOrDefault(d, new int[2]);
            Map<String, Object> cell = new java.util.LinkedHashMap<>();
            cell.put("date", d.format(DAY));
            cell.put("attempts", c[0]);
            cell.put("accepted", c[1]);
            out.add(cell);
        }
        return out;
    }

    @Transactional
    public void onSubmitResult(UserAccount user, String topicTags, boolean accepted, String verdict) {
        UserAccount u = users.findById(user.getId()).orElseThrow();
        u.setTotalAttempts(u.getTotalAttempts() + 1);
        if (accepted) {
            u.setTotalAccepted(u.getTotalAccepted() + 1);
            LocalDate today = LocalDate.now();
            if (u.getLastPracticeDate() == null) {
                u.setCurrentStreak(1);
            } else if (u.getLastPracticeDate().equals(today)) {
                // same day
            } else if (u.getLastPracticeDate().equals(today.minusDays(1))) {
                u.setCurrentStreak(u.getCurrentStreak() + 1);
            } else {
                u.setCurrentStreak(1);
            }
            u.setLastPracticeDate(today);
        } else if ("WA".equals(verdict) && topicTags != null && !topicTags.isBlank()) {
            String first = topicTags.split(",")[0].trim();
            Map<String, Integer> map = readMap(u.getTopicFailJson());
            map.put(first, map.getOrDefault(first, 0) + 1);
            String worst = first;
            int best = -1;
            for (Map.Entry<String, Integer> e : map.entrySet()) {
                if (e.getValue() > best) {
                    best = e.getValue();
                    worst = e.getKey();
                }
            }
            u.setSuggestedTopic(worst);
            try {
                u.setTopicFailJson(mapper.writeValueAsString(map));
            } catch (Exception ignored) {
            }
        }
        users.save(u);
    }

    @Transactional
    public void onHintUsed(UserAccount user) {
        UserAccount u = users.findById(user.getId()).orElseThrow();
        u.setHintUsage(u.getHintUsage() + 1);
        users.save(u);
    }

    private Map<String, Integer> readMap(String json) {
        try {
            if (json == null || json.isBlank()) {
                return new HashMap<>();
            }
            return mapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }
}
