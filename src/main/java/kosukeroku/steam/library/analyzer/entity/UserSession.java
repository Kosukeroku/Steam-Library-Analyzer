package kosukeroku.steam.library.analyzer.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;

@RedisHash("user_sessions")
@NoArgsConstructor
@Data
public class UserSession implements Serializable {

    @Id
    private Long chatId;
    private String steamId; // in resolved state
    private Long createdAt;

    @TimeToLive(unit = TimeUnit.HOURS)
    private Long ttl;

    public UserSession(Long chatId, String steamId, Long ttl) {
        this.chatId = chatId;
        this.steamId = steamId;
        this.createdAt = System.currentTimeMillis();
        this.ttl = ttl;
    }

}