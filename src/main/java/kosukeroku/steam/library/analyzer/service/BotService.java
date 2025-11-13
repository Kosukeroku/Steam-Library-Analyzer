package kosukeroku.steam.library.analyzer.service;

import kosukeroku.steam.library.analyzer.modelDTO.AchievementStats;
import kosukeroku.steam.library.analyzer.modelDTO.FriendGameStats;
import kosukeroku.steam.library.analyzer.modelDTO.GameStats;
import kosukeroku.steam.library.analyzer.modelDTO.SteamGame;
import kosukeroku.steam.library.analyzer.exception.SteamApiException;
import kosukeroku.steam.library.analyzer.exception.SteamPrivateProfileException;
import kosukeroku.steam.library.analyzer.exception.SteamUserNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BotService {

    private final SteamService steamService;
    private static final String LINE_SEPARATOR = "-----------------------------------------------------------------------------------";
    private static final String WELCOME_MESSAGE = """
    👋 *Hi! I am Steam Library Analyzer Bot!*
    
    Send me your:
    • 🔢 SteamID (17 digits) 
    • 🔗 Or custom profile name
    
    I'll show you:
    📊 Overall game statistics
    🎮 Top games by playtime
    🏆 Achievement progress
    
    *Examples:*
    `76561197960287930`
    `gabelogannewell`
    
    💡 _To find your Steam ID: Click your username → "Account details"_
    💡 _To find custom URL: Click your username → "Profile" → "Edit Profile"_
    """;

    public String handleMessage(String message, Long chatId) {
        log.info("Received message from chat {}: {}", chatId, message);

        if (message.equals("/start")) {
            return WELCOME_MESSAGE;
        }

        if (message.trim().isEmpty()) {
            return "❌ Please send me your SteamID or custom URL name.";
        }

        return processSteamInput(message.trim());
    }

    private String processSteamInput(String input) {
        try {
            String resolvedSteamId = steamService.resolveSteamId(input);
            List<SteamGame> games = steamService.getGames(resolvedSteamId);


            GameStats stats = steamService.getOverallStats(games);
            List<SteamGame> topGames = steamService.getTopGamesByPlaytime(games);
            AchievementStats achievementStats = steamService.getAchievementStats(resolvedSteamId);
            List<FriendGameStats> friendGames = steamService.getPopularGamesAmongFriends(resolvedSteamId);

            String statsMessage = steamService.formatStatsMessage(stats, input, resolvedSteamId);
            String topGamesMessage = steamService.formatTopGamesMessage(topGames);
            String achievementMessage = steamService.formatAchievementMessage(achievementStats);
            String friendGamesMessage = steamService.formatFriendGamesMessage(friendGames);

            return statsMessage + LINE_SEPARATOR + "\n" + topGamesMessage + LINE_SEPARATOR + "\n" + achievementMessage + LINE_SEPARATOR + "\n" + friendGamesMessage;

        } catch (SteamUserNotFoundException e) {
            log.warn("User not found: {}", input);
            return "❌ " + e.getMessage();

        } catch (SteamPrivateProfileException e) {
            log.warn("Private profile: {}", input);
            return "🔒 " + e.getMessage();

        } catch (SteamApiException e) {
            log.error("Steam API error for: {}", input, e);
            return "❌ " + e.getMessage();

        } catch (Exception e) {
            log.error("Unexpected error for: {}", input, e);
            return "❌ Server internal error. Please try again later.";
        }
    }
}