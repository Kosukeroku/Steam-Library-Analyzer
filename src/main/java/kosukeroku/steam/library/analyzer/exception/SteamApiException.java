package kosukeroku.steam.library.analyzer.exception;

public class SteamApiException extends RuntimeException {
    public SteamApiException(String message) {
        super("Steam API error: " + message);
    }


}