package kosukeroku.steam.library.analyzer.dto;


import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record SteamAchievementsResponse(
        @JsonProperty("playerstats") PlayerStats playerstats
) {
    public record PlayerStats(
            @JsonProperty("steamID") String steamId,
            @JsonProperty("gameName") String gameName,
            @JsonProperty("achievements") List<GameAchievement> achievements,
            @JsonProperty("success") Boolean success
    ) {}

    public record GameAchievement(
            @JsonProperty("apiname") String apiName,
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("achieved") Integer achieved,
            @JsonProperty("unlocktime") Long unlockTime
    ) {
        public boolean isAchieved() {
            return achieved == 1;
        }
    }
}
