package ms.hispam.budget.service;

import ms.hispam.budget.entity.mysql.UserSession;
import ms.hispam.budget.repository.mysql.UserSessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class UserSessionService {

    private final UserSessionRepository userSessionRepository;

    public UserSessionService(UserSessionRepository userSessionRepository) {
        this.userSessionRepository = userSessionRepository;
    }

    @Transactional("mysqlTransactionManager")
    public String createOrUpdateSession(String userId) {
        UserSession existingSession = userSessionRepository.findByUserIdAndActive(userId, true);
        if (existingSession != null) {
            existingSession.setLastAccessedAt(LocalDateTime.now());
            userSessionRepository.save(existingSession);
            return existingSession.getSessionId();
        } else {
            String sessionId = UUID.randomUUID().toString();
            UserSession newSession = new UserSession();
            newSession.setSessionId(sessionId);
            newSession.setUserId(userId);
            newSession.setCreatedAt(LocalDateTime.now());
            newSession.setLastAccessedAt(LocalDateTime.now());
            newSession.setActive(true);
            userSessionRepository.save(newSession);
            return sessionId;
        }
    }

    public boolean validateSession(String sessionId) {
        return userSessionRepository.findBySessionId(sessionId)
                .map(session -> {
                    session.setLastAccessedAt(LocalDateTime.now());
                    userSessionRepository.save(session);
                    return session.isActive();
                })
                .orElse(false);
    }

    public void invalidateSession(String sessionId) {
        userSessionRepository.findBySessionId(sessionId)
                .ifPresent(session -> {
                    session.setActive(false);
                    userSessionRepository.save(session);
                });
    }
}
