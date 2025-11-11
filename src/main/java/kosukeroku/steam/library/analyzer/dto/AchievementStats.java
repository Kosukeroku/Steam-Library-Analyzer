package kosukeroku.steam.library.analyzer.dto;

public record AchievementStats(
        int totalAchievements,
        int completedAchievements,
        double completionPercentage,
        int perfectGames,
        double averageCompletion,
        boolean hidden // whether achievement stats are hidden
) {}