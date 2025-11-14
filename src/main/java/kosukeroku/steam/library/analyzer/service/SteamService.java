package kosukeroku.steam.library.analyzer.service;


import kosukeroku.steam.library.analyzer.modelDTO.*;
import kosukeroku.steam.library.analyzer.responseDTO.*;
import kosukeroku.steam.library.analyzer.exception.SteamApiException;
import kosukeroku.steam.library.analyzer.exception.SteamPrivateProfileException;
import kosukeroku.steam.library.analyzer.exception.SteamUserNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SteamService {

    private final WebClient webClient;

    private static final int VANITY_SUCCESS = 1; // returned code if vanity url was successfully found
    private static final int VANITY_NOT_FOUND = 42; // returned code if vanity url was not found
    private static final int GAMES_IN_OUTPUT = 5;
    private static final int ACHIEVEMENTS_IN_OUTPUT = 3;
    private static final int OVERLAPS_IN_OUTPUT = 3;
    private static final int FRIENDS_IN_SHARING_OUTPUT = 3;
    private static final int FRIENDS_IN_LEADERBOARD_OUTPUT = 5;
    private static final int MINIMUM_HOURS_FOR_STATS = 10;

    @Value("${steam.api.key:}")
    private String steamApiKey;



    public SteamService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl("https://api.steampowered.com").build();
    }

    // converts vanityURL name to steamID
    public String resolveSteamId(String input) {
        log.info("Resolving SteamID for: {}", input);

        // if input is 17 digits, it is steamID
        if (input.matches("^\\d{17}$")) {
            log.info("Input is already SteamID64: {}", input);
            return input;
        }

        // otherwise we consider it a vanity url
        log.info("Treating input as vanity URL: {}", input);

        try {
            SteamVanityResponse response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/ISteamUser/ResolveVanityURL/v0001/")
                            .queryParam("key", steamApiKey)
                            .queryParam("vanityurl", input)
                            .build())
                    .retrieve()
                    .bodyToMono(SteamVanityResponse.class)
                    .block();

            // processing api response
            if (response != null && response.response() != null && response.response().success() == VANITY_SUCCESS) {
                String steamId = response.response().steamId();
                log.info("Successfully resolved '{}' to SteamID: {}", input, steamId);
                return steamId;
            } else {
                log.warn("Vanity URL not found: {}", input);
                throw new SteamUserNotFoundException(input);
            }

        } catch (SteamUserNotFoundException e) {
            throw e; // throwing known exception
        } catch (Exception e) {
            log.error("Error resolving vanity URL: {}", input, e);
            throw new SteamApiException("Error processing profile name.");
        }
    }

    public String getPlayerName(String steamId) {
        try {
            SteamPlayerSummariesResponse response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/ISteamUser/GetPlayerSummaries/v2/")
                            .queryParam("key", steamApiKey)
                            .queryParam("steamids", steamId)
                            .build())
                    .retrieve()
                    .bodyToMono(SteamPlayerSummariesResponse.class)
                    .block();

            if (response != null &&
                    response.response() != null &&
                    response.response().players() != null &&
                    !response.response().players().isEmpty()) {

                return response.response().players().get(0).personaName();
            }
        } catch (Exception e) {
            log.debug("Could not fetch name for user {}: {}", steamId, e.getMessage());
        }
        return "Unknown user";
    }

    public List<SteamGame> getGames(String steamId) {
        SteamOwnedGamesResponse response = getGamesResponse(steamId);
        return response.response().games();
    }

    /// //////////////////////////////////////////////
    // BASIC OVERALL STATS SECTION
    /// //////////////////////////////////////////////
    public GameStats getOverallStats(List<SteamGame> games) {
        log.info("Calculating overall stats {} games", games.size());


        // calculating stats
        int totalGames = games.size();
        int totalPlaytimeMinutes = games.stream()
                .mapToInt(SteamGame::playtime)
                .sum();
        int playedGames = (int) games.stream()
                .filter(game -> game.playtime() > 0)
                .count();
        int neverPlayedGames = totalGames - playedGames;

        double totalPlaytimeHours = totalPlaytimeMinutes / (double) 60;
        double averagePlaytimeHours = playedGames > 0 ? totalPlaytimeHours / playedGames : 0;
        double neverPlayedPercentage = totalGames > 0 ? (neverPlayedGames * 100.0) / totalGames : 0;

        return new GameStats(totalGames, totalPlaytimeMinutes, playedGames, neverPlayedGames,
                totalPlaytimeHours, averagePlaytimeHours, neverPlayedPercentage);
    }

    private SteamOwnedGamesResponse getGamesResponse(String steamId) {
        log.info("Fetching games library for SteamID: {}", steamId);

        SteamOwnedGamesResponse response = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/IPlayerService/GetOwnedGames/v0001/")
                        .queryParam("key", steamApiKey)
                        .queryParam("steamid", steamId)
                        .queryParam("include_appinfo", 1)
                        .queryParam("format", "json")
                        .build())
                .retrieve()
                .bodyToMono(SteamOwnedGamesResponse.class)
                .block();

        validateGamesResponse(response, steamId);
        return response;
    }

    private void validateGamesResponse(SteamOwnedGamesResponse response, String steamId) {
        if (response == null) {
            throw new SteamApiException("Empty response from Steam API");
        }
        if (response.response() == null) {
            throw new SteamPrivateProfileException(steamId);
        }
        if (response.response().games() == null) {
            throw new SteamPrivateProfileException(steamId);
        }
    }

    public String formatStatsMessage(GameStats stats, String originalInput, String resolvedSteamId) {
        StringBuilder message = new StringBuilder();
        String userNickname = getPlayerName(resolvedSteamId);

        message.append("👤 *User:* ").append(userNickname).append(" (SteamID: ").append(resolvedSteamId).append(")\n\n");

        message.append(String.format("""
        📊 *Overall Stats:*
        
        • Total games: %d
        • Total playtime: %,.0f hours  
        • Average per game: %,.1f hours
        • Games never played: %d (%.0f%%)
        """,
                stats.totalGames(), stats.totalPlaytimeHours(), stats.averagePlaytimeHours(),
                stats.neverPlayedGames(), stats.neverPlayedPercentage()));

        return message.toString();
    }




    /// //////////////////////////////////////////////
    // GAMES BY PLAYTIME SECTION
    /// //////////////////////////////////////////////
    public List<SteamGame> getTopGamesByPlaytime(List<SteamGame> games) {
        log.info("Getting top games from {} total games", games.size());

        return games.stream()
                .filter(game -> game.playtime() > 0 && game.name() != null)
                .sorted(Comparator.comparing(SteamGame::playtime).reversed())
                .limit(GAMES_IN_OUTPUT)
                .collect(Collectors.toList());

    }

    public String formatTopGamesMessage(List<SteamGame> topGames) {
        StringBuilder message = new StringBuilder();

        message.append("🎮 *Top ").append(GAMES_IN_OUTPUT).append(" games by playtime:*\n\n");

        for (int i = 0; i < topGames.size(); i++) {
            SteamGame game = topGames.get(i);
            int hours = game.playtime() / 60;
            int minutes = game.playtime() % 60;

            String timeString = hours > 0 ?
                    String.format("%d h %d min", hours, minutes) :
                    String.format("%d min", minutes);

            message.append("*").append(i + 1).append(".* ").append(game.name()).append("\n");
            message.append("    ⏱️ ").append(timeString).append("\n\n");
        }

        return message.toString();
    }

    /// //////////////////////////////////////////////
    // ACHIEVEMENT SECTION
    /// //////////////////////////////////////////////
    // utility records...
    public record AchievementData(String gameName, int totalAchievements, int completedAchievements, boolean isPerfect, List<SteamAchievementsResponse.GameAchievement> allAchievements) {}

    public record RecentAchievement(String achievementName, String gameName, Long unlockTime) {}

    // ...and utility methods for extracting achievement data from games
    private AchievementData getAchievementData(String steamId, SteamGame game) {
        List<SteamAchievementsResponse.GameAchievement> achievements =
                getGameAchievements(steamId, game.appId().toString());

        if (!achievements.isEmpty()) {
            int total = achievements.size();
            long completed = achievements.stream()
                    .filter(SteamAchievementsResponse.GameAchievement::isAchieved)
                    .count();

            return new AchievementData(game.name(), total, (int) completed, completed == total, achievements);
        }

        return new AchievementData(game.name(), 0, 0, false, Collections.emptyList());
    }

    private List<SteamAchievementsResponse.GameAchievement> getGameAchievements(String steamId, String appId) {
        try {

            SteamAchievementsResponse response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/ISteamUserStats/GetPlayerAchievements/v1/")
                            .queryParam("key", steamApiKey)
                            .queryParam("steamid", steamId)
                            .queryParam("appid", appId)
                            .queryParam("l", "english")
                            .build())
                    .retrieve()
                    .bodyToMono(SteamAchievementsResponse.class)
                    .block();

            if (response != null &&
                    response.playerstats() != null &&
                    Boolean.TRUE.equals(response.playerstats().success()) &&
                    response.playerstats().achievements() != null) {
                return response.playerstats().achievements();
            }

        } catch (Exception e) {
            log.debug("No achievements for appId {}: {}", appId, e.getMessage());
        }
        return Collections.emptyList();
    }

    public AchievementStats getAchievementStats(String steamId) {
        log.info("Calculating achievement stats for SteamID: {}", steamId);

        SteamOwnedGamesResponse response = getGamesResponse(steamId);
        List<SteamGame> games = response.response().games();

        List<SteamGame> playedGames = games.stream()
                .filter(game -> game.playtime() > 0)
                .toList();

        log.info("Processing {} played games for achievements", playedGames.size());

        // checking if achievements are hidden by testing the first game for 403 response
        if (!playedGames.isEmpty()) {
            SteamGame firstGame = playedGames.get(0);
            try {
                SteamAchievementsResponse testResponse = webClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/ISteamUserStats/GetPlayerAchievements/v1/")
                                .queryParam("key", steamApiKey)
                                .queryParam("steamid", steamId)
                                .queryParam("appid", firstGame.appId().toString())
                                .queryParam("l", "english")
                                .build())
                        .retrieve()
                        .bodyToMono(SteamAchievementsResponse.class)
                        .block();

            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("403")) {
                    log.warn("Profile is hidden - 403 Forbidden for appId: {}", firstGame.appId());
                    return new AchievementStats(0, 0, 0, 0, 0, true, Collections.emptyList(), Collections.emptyList());
                }
            }
        }

        List<AchievementData> achievementData = playedGames.parallelStream()
                .map(game -> getAchievementData(steamId, game))
                .filter(data -> data.totalAchievements > 0)
                .toList();

        // sorting by completion percentage
        List<AchievementData> topByProgress = achievementData.stream()
                .sorted(Comparator.comparingDouble((AchievementData data) ->
                                (double) data.completedAchievements() / data.totalAchievements() * 100)
                        .reversed())
                .limit(GAMES_IN_OUTPUT)
                .collect(Collectors.toList());

        // getting recent achievements
        List<RecentAchievement> recentAchievements = achievementData.parallelStream()
                .flatMap(data -> data.allAchievements().stream()
                        .filter(SteamAchievementsResponse.GameAchievement::isAchieved)
                        .map(achievement -> new RecentAchievement(
                                achievement.name(),
                                data.gameName(),
                                achievement.unlockTime()
                        ))
                )
                .sorted(Comparator.comparingLong(RecentAchievement::unlockTime).reversed())
                .limit(ACHIEVEMENTS_IN_OUTPUT)
                .collect(Collectors.toList());

        int totalAchievements = achievementData.stream().mapToInt(AchievementData::totalAchievements).sum();
        int completedAchievements = achievementData.stream().mapToInt(AchievementData::completedAchievements).sum();
        int perfectGames = (int) achievementData.stream().filter(AchievementData::isPerfect).count();
        int gamesWithAchievements = achievementData.size();

        double completionPercentage = totalAchievements > 0 ?
                (double) completedAchievements / totalAchievements * 100 : 0;

        double totalCompletionPercent = achievementData.stream()
                .mapToDouble(data -> (double) data.completedAchievements() / data.totalAchievements() * 100)
                .sum();

        double averageCompletion = gamesWithAchievements > 0 ?
                totalCompletionPercent / gamesWithAchievements : 0;

        log.info("Found {} games with achievements", gamesWithAchievements);



        return new AchievementStats(
                totalAchievements,
                completedAchievements,
                completionPercentage,
                perfectGames,
                averageCompletion,
                false,
                topByProgress,
                recentAchievements
        );
    }



    public String formatAchievementMessage(AchievementStats stats) {
        if (stats.hidden()) {
            return """
        🏆 *Achievement Overview:*
        
        🔒 Achievement data is hidden
        • Make sure your *Game Details* are set to *Public*
        
        💡 _How to fix:
        Steam → Settings → Privacy → Game Details → Public_
        """;
        }

        StringBuilder message = new StringBuilder();

        message.append(String.format("""
        🏆 *Achievement Overview:*
        
        • Total achievements (out of all possible): %,d/%,d (%.1f%%)
        • Perfect games: %,d
        • Average completion per game: %.1f%%
        """,
                stats.completedAchievements(),
                stats.totalAchievements(),
                stats.completionPercentage(),
                stats.perfectGames(),
                stats.averageCompletion()
        ));

        // top by progress
        if (!stats.topGamesByProgress().isEmpty()) {
            message.append("\n🎯 *Top Games by Achievement Progress:*\n\n");

            for (int i = 0; i < stats.topGamesByProgress().size(); i++) {
                AchievementData data = stats.topGamesByProgress().get(i);
                double percentage = (double) data.completedAchievements() / data.totalAchievements() * 100;
                message.append(String.format(
                        "*%d.* %s – %,d/%,d (%.0f%%)\n",
                        i + 1,
                        data.gameName(),
                        data.completedAchievements(),
                        data.totalAchievements(),
                        percentage
                ));
            }
        }

        // most recent
        if (!stats.recentAchievements().isEmpty()) {
            message.append("\n🆕 *Recently Unlocked:*\n\n");

            for (RecentAchievement achievement : stats.recentAchievements()) {
                String timeAgo = convertToTimeAgo(achievement.unlockTime());
                message.append(String.format("• %s – %s (%s)\n",
                        achievement.achievementName(),
                        achievement.gameName(),
                        timeAgo
                ));
            }
        }

        return message.toString();
    }

    // converts achievements' unlock time into "[n] [unit(s)] ago"
    private String convertToTimeAgo(Long unlockTime) {
        if (unlockTime == null) return "unknown";

        long diffSeconds = (System.currentTimeMillis() / 1000) - unlockTime;

        if (diffSeconds < 60) return "just now";

        String unit;
        long value;

        if (diffSeconds < 3600) {
            value = diffSeconds / 60;
            unit = "minute";
        } else if (diffSeconds < 86400) {
            value = diffSeconds / 3600;
            unit = "hour";
        } else if (diffSeconds < 2592000) {
            value = diffSeconds / 86400;
            unit = "day";
        } else if (diffSeconds < 31536000) {
            value = diffSeconds / 2592000;
            unit = "month";
        } else {
            value = diffSeconds / 31536000;
            unit = "year";
        }

        return value + " " + unit + (value > 1 ? "s" : "") + " ago";
    }


    /// //////////////////////////////////////////////
    // FRIENDS SECTION
    /// //////////////////////////////////////////////

    // utility record for aggregating friends' stats by game
    private record GameAggregate(String gameName, int friendCount, int totalPlaytime) {}

    // and utility method for getting friends' id
    private List<String> getFriendIds(String steamId) {
        log.info("Fetching friends' SteamIDs for Steam ID: {}", steamId);
        try {
            SteamFriendsResponse response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/ISteamUser/GetFriendList/v1/")
                            .queryParam("key", steamApiKey)
                            .queryParam("steamid", steamId)
                            .queryParam("relationship", "friend")
                            .build())
                    .retrieve()
                    .bodyToMono(SteamFriendsResponse.class)
                    .block();

            if (response != null && response.friendslist() != null && response.friendslist().friends() != null) {
                return response.friendslist().friends().stream()
                        .map(SteamFriendsResponse.Friend::steamId)
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            // 401 means friend list is hidden, in that case we return null
            if (e.getMessage() != null && e.getMessage().contains("401")) {
                log.warn("Friends list is hidden for {}: 401 Unauthorized", steamId);
                return null;
            }
            log.warn("Could not fetch friends list for {}: {}", steamId, e.getMessage());
        }
        return Collections.emptyList();
    }


    // returns a map 'steamID -> nickname'
    private Map<String, String> getFriendNames(List<String> friendIds) {
        if (friendIds.isEmpty()) return Collections.emptyMap();

        try {

            // merging all friends' IDs into a single string for an API request
            String steamIds = String.join(",", friendIds);
            log.info("Friends' SteamIDs in a single string: {}", steamIds);

            SteamPlayerSummariesResponse response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/ISteamUser/GetPlayerSummaries/v2/")
                            .queryParam("key", steamApiKey)
                            .queryParam("steamids", steamIds)
                            .build())
                    .retrieve()
                    .bodyToMono(SteamPlayerSummariesResponse.class)
                    .block();

            if (response != null && response.response() != null && response.response().players() != null) {
                return response.response().players().stream()
                        .collect(Collectors.toMap(
                                SteamPlayerSummariesResponse.Player::steamId,
                                SteamPlayerSummariesResponse.Player::personaName
                        ));
            }
        } catch (Exception e) {
            log.debug("Could not fetch friend names: {}", e.getMessage());
        }

        return Collections.emptyMap();
    }

    public List<FriendGameStats> getPopularGamesAmongFriends(String steamId) {
        log.info("Getting popular games among friends for SteamID: {}", steamId);

        // getting a friend list
        List<String> friendIds = getFriendIds(steamId);

        // if we got null from our utility method, the user has his friend list hidden
        if (friendIds == null) {
            log.info("Friends list is hidden for user: {}", steamId);

            // in which case we return a list of one element with the hidden field set to true
            return List.of(new FriendGameStats("", 0L, 0, 0, 0, true));
        }

        if (friendIds.isEmpty()) {
            log.info("No friends found for user: {}", steamId);
            return Collections.emptyList();
        }

        log.info("Found {} friends, analyzing their libraries", friendIds.size());

        // collecting friends' stats
        Map<Long, GameAggregate> gameStats = new HashMap<>();

        friendIds.parallelStream().forEach(friendId -> {
            try {
                List<SteamGame> friendGames = getGames(friendId);

                // updating aggregated stats for every friend's game
                friendGames.forEach(game -> {
                    gameStats.compute(game.appId(), (appId, aggregate) -> {

                        // if we don't have stats for this game yet, we create it
                        if (aggregate == null) {
                            return new GameAggregate(game.name(), 1, game.playtime());
                        }

                        // otherwise we update the stats by increasing the friend count by one and total playtime by this friend's playtime
                        return new GameAggregate(
                                aggregate.gameName(),
                                aggregate.friendCount() + 1,
                                aggregate.totalPlaytime() + game.playtime()
                        );
                    });
                });
            } catch (Exception e) {
                log.debug("Could not fetch games for friend {}. Reason: {}", friendId, e.getMessage());
            }
        });

        // sorting
        return gameStats.entrySet().stream()
                .map(entry -> {
                    GameAggregate agg = entry.getValue();
                    double avgHours = agg.totalPlaytime() / (double) agg.friendCount() / 60;
                    return new FriendGameStats(
                            agg.gameName(),
                            entry.getKey(),
                            agg.friendCount(),
                            avgHours,
                            agg.totalPlaytime() / 60,
                            false
                    );
                })
                .filter(stats -> stats.avgPlaytimeHours() > MINIMUM_HOURS_FOR_STATS)
                .sorted((s1, s2) -> {
                    int compare = s2.friendCount() - s1.friendCount();
                    if (compare != 0) {
                        return compare;
                    }
                    return Double.compare(s2.avgPlaytimeHours(), s1.avgPlaytimeHours());

                })
                .limit(GAMES_IN_OUTPUT)
                .collect(Collectors.toList());
    }



    // calculates shared games info for one friend
    private FriendGameOverlap calculateOverlapWithFriend(String friendId, Map<String, String> friendNames, Set<Long> myGameIds, List<SteamGame> myGames) {
        try {
            // getting friend's games
            List<SteamGame> friendGames = getGames(friendId);
            Set<Long> friendGameIds = friendGames.stream()
                    .map(SteamGame::appId)
                    .collect(Collectors.toSet());

            // finding shared games
            Set<Long> sharedGameIds = new HashSet<>(myGameIds);
            sharedGameIds.retainAll(friendGameIds); // keeping only shared games

            // getting most played shared games
            List<String> sampleGames = myGames.stream()
                    .filter(game -> sharedGameIds.contains(game.appId()))
                    .sorted(Comparator.comparing(SteamGame::playtime).reversed())
                    .map(SteamGame::name)
                    .limit(FRIENDS_IN_SHARING_OUTPUT)
                    .collect(Collectors.toList());

            // getting friend's name from the map
            String friendName = friendNames.getOrDefault(friendId, "Friend");

            return new FriendGameOverlap(friendName, friendId, sharedGameIds.size(), sampleGames);

        } catch (Exception e) {
            log.debug("Could not calculate overlap with friend {}: {}", friendId, e.getMessage());
            return new FriendGameOverlap("Friend", friendId, 0, Collections.emptyList());
        }
    }


    public List<FriendGameOverlap> getTopGameOverlaps(String steamId) {
        log.info("Calculating game overlaps for SteamID: {}", steamId);

        // getting user's games
        List<SteamGame> myGames = getGames(steamId);
        Set<Long> myGameIds = myGames.stream()
                .map(SteamGame::appId)
                .collect(Collectors.toSet());

        // getting user's friends' IDs
        List<String> friendIds = getFriendIds(steamId);
        if (friendIds == null || friendIds.isEmpty()) {
            return Collections.emptyList();
        }

        // getting user's friends' nicknames
        Map<String, String> friendNames = getFriendNames(friendIds);

        // finding shared games for each of the friends
        return friendIds.parallelStream()
                .map(friendId -> calculateOverlapWithFriend(
                        friendId,
                        friendNames,
                        myGameIds,
                        myGames
                ))
                .sorted(Comparator.comparingInt(FriendGameOverlap::sharedGamesCount).reversed())
                .limit(FRIENDS_IN_SHARING_OUTPUT)
                .collect(Collectors.toList());
    }

    public List<FriendAchievementLeaderboard> getAchievementLeaderboard(String steamId) {
        log.info("Building achievement leaderboard for SteamID: {}", steamId);

        List<String> friendIds = getFriendIds(steamId);
        if (friendIds == null) {
            return Collections.emptyList(); // friends are hidden
        }

        // adding user's ID to a list of their friends' IDs
        List<String> allUsers = new ArrayList<>(friendIds);
        allUsers.add(steamId);

        Map<String, String> userNames = getFriendNames(allUsers);


        return allUsers.parallelStream()
                .map(userId -> {
                    try {
                        AchievementStats achievementStats = getAchievementStats(userId);

                        String name = userNames.getOrDefault(userId, "Unknown");
                        boolean isCurrentUser = userId.equals(steamId); // if the current processed ID is user's, set this to true for further processing

                        return new FriendAchievementLeaderboard(
                                name,
                                userId,
                                achievementStats.completedAchievements(),
                                isCurrentUser
                        );
                    } catch (SteamPrivateProfileException e) {
                        log.debug("Private profile for {}: {}", userId, e.getMessage());
                        return new FriendAchievementLeaderboard(
                                userNames.getOrDefault(userId, "Private Profile"),
                                userId, 0, false
                        );
                    } catch (Exception e) {
                        log.debug("Could not fetch achievements for user {}: {}", userId, e.getMessage());
                        return new FriendAchievementLeaderboard(
                                userNames.getOrDefault(userId, "Unknown"),
                                userId, 0, false
                        );
                    }
                })
                .sorted(Comparator.comparingInt(FriendAchievementLeaderboard::totalAchievements).reversed())
                .limit(FRIENDS_IN_LEADERBOARD_OUTPUT)
                .collect(Collectors.toList());
    }


    public String formatFriendGamesMessage(List<FriendGameStats> friendGames, List<FriendGameOverlap> overlaps, List<FriendAchievementLeaderboard> leaderboard, AchievementStats userAchievementStats, String nickname) {

        // if the friend list is hidden, then the friend games list that this method accepts will only have one element, so we can
        // get the first element and check the value of its 'hidden' field
        boolean isFriendsHidden = !friendGames.isEmpty() && friendGames.get(0).hidden();

        if (isFriendsHidden) {
            return """
        👥 *Friends Overview:*
        
        🔒 Friends list is hidden
        • Make sure your *Friends List* is set to *Public*
        
        💡 _How to fix:
        Steam → Profile → Edit Profile → Privacy Settings → Friends List → Public_
        """;
        }


        if (friendGames.isEmpty()) {
            return "";
        }

        StringBuilder message = new StringBuilder();
        message.append("👥 *Popular Among Friends:*\n\n");

        for (int i = 0; i < friendGames.size(); i++) {
            FriendGameStats stats = friendGames.get(i);
            message.append(String.format(
                    "*%d.* %s - %d friends, %.0f avg hours\n",
                    i + 1,
                    stats.gameName(),
                    stats.friendCount(),
                    stats.avgPlaytimeHours()
            ));
        }

        if (!overlaps.isEmpty()) {
            message.append("\n🎮 *Games You Share:*\n\n");

            for (FriendGameOverlap overlap : overlaps) {
                String gamesSample = String.join(", ", overlap.sampleGames());
                message.append(String.format(
                        "• With *%s*: %d games _(Most played are: %s)_\n",
                        overlap.friendName(),
                        overlap.sharedGamesCount(),
                        gamesSample
                ));
            }

        }

        if (!leaderboard.isEmpty()) {
            message.append("\n🏅 *Achievement Leaderboard (only friends with public achievement info):*\n\n");

            String[] medals = {"🥇", "🥈", "🥉"};

            for (int i = 0; i < leaderboard.size(); i++) {
                FriendAchievementLeaderboard entry = leaderboard.get(i);
                String medal = i < 3 ? medals[i] : (i + 1) + "."; // adding medals for places 1-3, and simple dot for others
                String name = entry.isCurrentUser() ? "👤*" + entry.friendName() + "*" : entry.friendName();


                message.append(String.format(
                        "%s %s - %,d achievements\n",
                        medal, name, entry.totalAchievements()
                ));
            }
        }
        boolean currentUserInLeaderboard = leaderboard.stream()
                .anyMatch(FriendAchievementLeaderboard::isCurrentUser);

        if (!currentUserInLeaderboard && userAchievementStats != null) {
            message.append("............\n");
            message.append(String.format(
                    "👤 *%s* - %,d achievements",
                    nickname,
                    userAchievementStats.completedAchievements()
            ));
        }

        return message.toString();
    }

}

