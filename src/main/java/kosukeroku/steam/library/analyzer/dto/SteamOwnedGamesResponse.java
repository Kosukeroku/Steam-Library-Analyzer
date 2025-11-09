package kosukeroku.steam.library.analyzer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record SteamOwnedGamesResponse(
        @JsonProperty("response") Response response
) {
    public record Response(
            @JsonProperty("game_count") Integer gameCount,
            @JsonProperty("games") List<SteamGame> games
    ) {}
}