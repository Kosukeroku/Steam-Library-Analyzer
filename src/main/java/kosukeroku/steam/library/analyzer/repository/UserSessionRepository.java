package kosukeroku.steam.library.analyzer.repository;


import kosukeroku.steam.library.analyzer.entity.UserSession;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserSessionRepository extends CrudRepository<UserSession, Long> {
    Optional<UserSession> findByChatId(Long chatId);
}