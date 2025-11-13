package kosukeroku.steam.library.analyzer.modelDTO;

import java.util.List;

public record FriendGameOverlap(
        String friendName,      // friend's name (actual nickname you see on steam, 'personaname' in steamAPI)
        String friendSteamId,   // friend's steamID
        int sharedGamesCount,   // total amount of shared games
        List<String> sampleGames // some of the shared games
) {}