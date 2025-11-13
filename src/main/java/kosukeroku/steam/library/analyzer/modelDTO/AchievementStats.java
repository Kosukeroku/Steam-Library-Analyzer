package kosukeroku.steam.library.analyzer.modelDTO;

import kosukeroku.steam.library.analyzer.service.SteamService;

import java.util.List;

public record AchievementStats(
        int totalAchievements,
        int completedAchievements,
        double completionPercentage,
        int perfectGames,
        double averageCompletion,
        boolean hidden, // whether achievement stats are hidden
        List<SteamService.AchievementData> topGamesByProgress,
        List<SteamService.RecentAchievement> recentAchievements
) {}