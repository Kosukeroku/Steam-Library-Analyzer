package kosukeroku.steam.library.analyzer.modelDTO;

public record GameStats(
        int totalGames,
        int totalPlaytimeMinutes,
        int playedGames,
        int neverPlayedGames,
        double totalPlaytimeHours,
        double averagePlaytimeHours,
        double neverPlayedPercentage
) {}