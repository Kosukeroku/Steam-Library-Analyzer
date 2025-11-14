package kosukeroku.steam.library.analyzer.modelDTO;

public record FriendAchievementLeaderboard(
    String friendName,
    String steamId,
    int totalAchievements,
    boolean isCurrentUser
) {}

