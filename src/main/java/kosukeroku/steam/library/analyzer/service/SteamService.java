package kosukeroku.steam.library.analyzer.service;


import kosukeroku.steam.library.analyzer.dto.*;
import kosukeroku.steam.library.analyzer.exception.SteamApiException;
import kosukeroku.steam.library.analyzer.exception.SteamPrivateProfileException;
import kosukeroku.steam.library.analyzer.exception.SteamUserNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SteamService {

    private final WebClient webClient;

    private static final int VANITY_SUCCESS = 1; // returned code if vanity url was successfully found
    private static final int VANITY_NOT_FOUND = 42; // returned code if vanity url was not found
    private static final int GAMES_IN_OUTPUT = 5;
    private static final int MINUTES_IN_HOUR = 60;

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

    // gets basic overall stats for an account
    public GameStats getOverallStats(String steamId) {
        log.info("Calculating overall stats for SteamID: {}", steamId);

        SteamOwnedGamesResponse response = getGamesResponse(steamId);
        List<SteamGame> games = response.response().games();

        // calculating stats
        int totalGames = games.size();
        int totalPlaytimeMinutes = games.stream()
                .mapToInt(SteamGame::playtime)
                .sum();
        int playedGames = (int) games.stream()
                .filter(game -> game.playtime() > 0)
                .count();
        int neverPlayedGames = totalGames - playedGames;

        double totalPlaytimeHours = totalPlaytimeMinutes / (double) MINUTES_IN_HOUR;
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

        // showing both steamID and custom name if the latter was used, and only steamID otherwise
        if (!originalInput.equals(resolvedSteamId)) {
            message.append("👤 *User:* ").append(originalInput)
                    .append(" (SteamID: ").append(resolvedSteamId).append(")\n\n");
        } else {
            message.append("👤 *SteamID:* ").append(resolvedSteamId).append("\n\n");
        }

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

    public List<SteamGame> getTopGamesByPlaytime(String steamId) {
        log.info("Getting top games for SteamID: {}", steamId);

        SteamOwnedGamesResponse response = getGamesResponse(steamId);
        List<SteamGame> games = response.response().games();

        List<SteamGame> filteredGames = games.stream()
                .filter(game -> game.playtime() > 0 && game.name() != null)
                .sorted(Comparator.comparing(SteamGame::playtime).reversed())
                .limit(GAMES_IN_OUTPUT)
                .collect(Collectors.toList());

        return filteredGames;

    }

    public String formatTopGamesMessage(List<SteamGame> topGames) {
        StringBuilder message = new StringBuilder();

        message.append("🎮 *Top ").append(GAMES_IN_OUTPUT).append(" games by playtime:*\n\n");

        for (int i = 0; i < topGames.size(); i++) {
            SteamGame game = topGames.get(i);
            int hours = game.playtime() / MINUTES_IN_HOUR;
            int minutes = game.playtime() % MINUTES_IN_HOUR;

            String timeString = hours > 0 ?
                    String.format("%d h %d min", hours, minutes) :
                    String.format("%d min", minutes);

            message.append("*").append(i + 1).append(".* ").append(game.name()).append("\n");
            message.append("    ⏱️ ").append(timeString).append("\n\n");
        }

        return message.toString();
    }

    public AchievementStats getAchievementStats(String steamId) {
        log.info("Calculating achievement stats for SteamID: {}", steamId);

        SteamOwnedGamesResponse response = getGamesResponse(steamId);
        List<SteamGame> games = response.response().games();

        List<SteamGame> playedGames = games.stream()
                .filter(game -> game.playtime() > 0)
                .collect(Collectors.toList());

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
                    return new AchievementStats(0, 0, 0, 0, 0, true, Collections.emptyList());
                }
            }
        }

        List<AchievementData> achievementData = playedGames.parallelStream()
                .map(game -> getAchievementData(steamId, game))
                .filter(data -> data.totalAchievements > 0)
                .collect(Collectors.toList());

        // sorting by completion percentage
        List<AchievementData> topByProgress = achievementData.stream()
                .sorted(Comparator.comparingDouble((AchievementData data) ->
                                (double) data.completedAchievements() / data.totalAchievements() * 100)
                        .reversed())
                .limit(GAMES_IN_OUTPUT)
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
                topByProgress
        );
    }

    // utility record...
    public record AchievementData(String gameName, int totalAchievements, int completedAchievements, boolean isPerfect) {}

    // ...and utility methods for extracting achievement data from games
    private AchievementData getAchievementData(String steamId, SteamGame game) {
        List<SteamAchievementsResponse.GameAchievement> achievements =
                getGameAchievements(steamId, game.appId().toString());

        if (!achievements.isEmpty()) {
            int total = achievements.size();
            long completed = achievements.stream()
                    .filter(SteamAchievementsResponse.GameAchievement::isAchieved)
                    .count();

            return new AchievementData(game.name(), total, (int) completed, completed == total);
        }

        return new AchievementData(game.name(), 0, 0, false);
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

        return message.toString();
    }
}

