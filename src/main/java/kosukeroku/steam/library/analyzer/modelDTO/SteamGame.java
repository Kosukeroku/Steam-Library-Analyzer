package kosukeroku.steam.library.analyzer.modelDTO;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SteamGame(
        @JsonProperty("appid") Long appId,
        @JsonProperty("name") String name,
        @JsonProperty("playtime_forever") Integer playtime,
        @JsonProperty("img_icon_url") String imgIconUrl
) {}