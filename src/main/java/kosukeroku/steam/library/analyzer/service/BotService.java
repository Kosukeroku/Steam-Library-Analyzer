package kosukeroku.steam.library.analyzer.service;

import kosukeroku.steam.library.analyzer.dto.SteamGame;
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
    private static final String WELCOME_MESSAGE = """
        👋 Hi! I am Steam Library Analyzer Bot!
        
        Send me:
        • 🔢 Your SteamID (17 digits)
        • 🔗 Or your unique name from custom URL
        
        **Examples:**
        `76561197960287930` - SteamID64
        `gabelogannewell` - Custom URL name
        
        💡 _To find your Steam ID, click your username in the top right corner of the Steam client or website and select "Account details".
        💡 To find your custom Steam profile URL, click on your username in the top right, select "Profile" or "View my profile", then click "Edit Profile". Your custom URL is located under the "General" tab._
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
            List<SteamGame> topGames = steamService.getTopGamesByPlaytime(resolvedSteamId);
            return steamService.formatTopGamesMessage(topGames, input, resolvedSteamId);

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