package kosukeroku.steam.library.analyzer.responseDTO;


import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record SteamPlayerSummariesResponse(
        @JsonProperty("response") Response response
) {

    // steam always returns an array, even in case of queries for one player
    public record Response(
            @JsonProperty("players") List<Player> players
    ) {}

    public record Player(
            @JsonProperty("steamid") String steamId,
            @JsonProperty("personaname") String personaName,
            @JsonProperty("profileurl") String profileUrl,
            @JsonProperty("avatar") String avatar,
            @JsonProperty("personastate") Integer personaState
    ) {}
}