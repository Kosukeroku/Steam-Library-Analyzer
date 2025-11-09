package kosukeroku.steam.library.analyzer.exception;

public class SteamPrivateProfileException extends RuntimeException {
    public SteamPrivateProfileException(String steamId) {
        super("Profile " + steamId + " is private. Please make it public and try again.");
    }
}