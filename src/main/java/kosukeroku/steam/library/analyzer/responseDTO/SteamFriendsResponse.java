package kosukeroku.steam.library.analyzer.responseDTO;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record SteamFriendsResponse(
        @JsonProperty("friendslist") FriendsList friendslist
) {
    public record FriendsList(
            @JsonProperty("friends") List<Friend> friends
    ) {}

    public record Friend(
            @JsonProperty("steamid") String steamId,
            @JsonProperty("relationship") String relationship,
            @JsonProperty("friend_since") Long friendSince
    ) {}
}