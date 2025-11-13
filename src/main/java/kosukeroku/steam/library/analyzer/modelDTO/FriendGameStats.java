package kosukeroku.steam.library.analyzer.modelDTO;

public record FriendGameStats(
        String gameName,
        Long appId,
        int friendCount, // how many friends own the game
        double avgPlaytimeHours, // their average playtime
        int totalPlaytimeHours, // their total playtime
        boolean hidden // whether friend list is hidden
) {}