package kosukeroku.steam.library.analyzer.service;

import kosukeroku.steam.library.analyzer.entity.UserSession;
import kosukeroku.steam.library.analyzer.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final UserSessionRepository sessionRepository;

    // default ttl is 24h, we are currently using 1h
    @Value("${app.session.ttl-hours:24}")
    private Long sessionTtlHours;

    public void createSession(Long chatId, String steamId) {
        UserSession session = new UserSession(chatId, steamId, sessionTtlHours);
        sessionRepository.save(session);
        log.info("Created session for chat {} with SteamID {}", chatId, steamId);
    }

    public Optional<String> getSteamId(Long chatId) {
        return sessionRepository.findByChatId(chatId)
                .map(UserSession::getSteamId);
    }

}