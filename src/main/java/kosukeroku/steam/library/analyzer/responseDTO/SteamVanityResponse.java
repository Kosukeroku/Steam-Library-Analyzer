package kosukeroku.steam.library.analyzer.responseDTO;


import com.fasterxml.jackson.annotation.JsonProperty;

public record SteamVanityResponse(
        @JsonProperty("response") Response response
) {
    public record Response(
            @JsonProperty("steamid") String steamId,
            @JsonProperty("success") Integer success // 1 for success, 42 for fail
    ) {}
}