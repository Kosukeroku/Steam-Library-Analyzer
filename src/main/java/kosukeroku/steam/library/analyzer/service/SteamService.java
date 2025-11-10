package kosukeroku.steam.library.analyzer.service;


import kosukeroku.steam.library.analyzer.dto.GameStats;
import kosukeroku.steam.library.analyzer.dto.SteamGame;
import kosukeroku.steam.library.analyzer.dto.SteamOwnedGamesResponse;
import kosukeroku.steam.library.analyzer.dto.SteamVanityResponse;
import kosukeroku.steam.library.analyzer.exception.SteamApiException;
import kosukeroku.steam.library.analyzer.exception.SteamPrivateProfileException;
import kosukeroku.steam.library.analyzer.exception.SteamUserNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

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

        log.info("Found {} games after filtering", filteredGames.size());
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
}

