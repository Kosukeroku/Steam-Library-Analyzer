package kosukeroku.steam.library.analyzer.exception;

public class SteamUserNotFoundException extends RuntimeException {

    public SteamUserNotFoundException(String input) {
        super("User '" + input + "' not found. " + "Check your custom URL name or use SteamID64");
    }
}