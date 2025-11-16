package kosukeroku.steam.library.analyzer.service;

import kosukeroku.steam.library.analyzer.modelDTO.*;
import kosukeroku.steam.library.analyzer.exception.SteamApiException;
import kosukeroku.steam.library.analyzer.exception.SteamPrivateProfileException;
import kosukeroku.steam.library.analyzer.exception.SteamUserNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BotService {

    private final SteamService steamService;
    private final SessionService sessionService;
    private final static String NEXT_ACTION_MESSAGE = "What would you like to know next?";
    private final static String NEW_PROFILE_HINT = "🔄 _To analyze a different profile, simply send another SteamID or custom URL_";
    private static final String WELCOME_MESSAGE = """
👋 *Hi! I am Steam Library Analyzer Bot!*
    
Send me your:
• 🔢 SteamID (17 digits)
• 🔗 Or custom profile name

I'll show you:
📊 Overall game statistics and playtime
🎮 Top games by playtime
🏆 Achievement progress and recent unlocks
👥 Friends comparison and leaderboards

*Examples:*
`76561197960287930`
`gabelogannewell`

💡 _To find your Steam ID: Click your username → "Account details"_
💡 _To find custom URL: Click your username → "Profile" → "Edit Profile"_
""";

    // shows the welcome message for the '/start' command and initiates the processing in the method below in other cases
    public String handleInitialMessage(String message, Long chatId) {
        if (message.equals("/start")) {
            return WELCOME_MESSAGE;
        }

        if (message.trim().isEmpty()) {
            return "❌ Please send me your SteamID or custom URL name.";
        }

        return processInitialSteamInput(message.trim(), chatId);
    }


    // processes an input that expects steamID (which is any text input besides '/start' at this moment)
    private String processInitialSteamInput(String input, Long chatId) {
        try {
            String resolvedSteamId = steamService.resolveSteamId(input);
            List<SteamGame> games = steamService.getGames(resolvedSteamId);
            GameStats stats = steamService.getOverallStats(games);

            // creating a redis session and storing steamID there
            sessionService.createSession(chatId, resolvedSteamId);

            String statsMessage = steamService.formatStatsMessage(stats, resolvedSteamId);
            return statsMessage + "\n\n**What would you like to know?**\n\n" + NEW_PROFILE_HINT;

        } catch (SteamUserNotFoundException e) {
            return "❌ " + e.getMessage();
        } catch (SteamPrivateProfileException e) {
            return "🔒 " + e.getMessage();
        } catch (Exception e) {
            log.error("Error processing Steam input for chat {}: {}", chatId, e.getMessage());
            return "❌ Server internal error. Please try again later.";
        }
    }

    // processes button responses (top games info, achievements info, friends stats)
    public String handleButtonResponse(String buttonData, Long chatId) {

        // getting steamID from a redis session
        Optional<String> steamIdOpt = sessionService.getSteamId(chatId);

        if (steamIdOpt.isEmpty()) {
            return "❌ Session expired or not found. Please send your SteamID again.";
        }

        String steamId = steamIdOpt.get();
        String nickname = steamService.getPlayerName(steamId);

        try {
            String result;
            switch (buttonData) {
                case "top_games":
                    List<SteamGame> games = steamService.getGames(steamId);
                    List<SteamGame> topGames = steamService.getTopGamesByPlaytime(games);
                    result = steamService.formatTopGamesMessage(topGames);
                    break;

                case "achievements":
                    AchievementStats achievementStats = steamService.getAchievementStats(steamId);
                    result = steamService.formatAchievementMessage(achievementStats);
                    break;

                case "friends":
                    List<FriendGameStats> friendGames = steamService.getPopularGamesAmongFriends(steamId);
                    List<FriendGameOverlap> friendOverlap = steamService.getTopGameOverlaps(steamId);
                    List<FriendAchievementLeaderboard> leaderboard = steamService.getAchievementLeaderboard(steamId);
                    AchievementStats achievementStatsForFriends = steamService.getAchievementStats(steamId);
                    result = steamService.formatFriendGamesMessage(friendGames, friendOverlap, leaderboard, achievementStatsForFriends, nickname);
                    break;

                default:
                    return "❌ Unknown command.";
            }

            return result + "\n\n" + NEXT_ACTION_MESSAGE +"\n\n" + NEW_PROFILE_HINT;

        } catch (SteamPrivateProfileException e) {
            return "🔒 " + e.getMessage();
        } catch (Exception e) {
            log.error("Error processing button {} for chat {}.", buttonData, chatId);
            return "❌ Error processing request. Please try again.";
        }
    }
}