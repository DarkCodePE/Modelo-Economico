package ms.hispam.budget.repository.mysql;

import ms.hispam.budget.entity.mysql.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {
    Optional<UserSession> findBySessionId(String sessionId);
    //Optional<UserSession> findByUserIdAndActive(String userId, boolean active);
    List<UserSession> findByUserId(String userId);
    UserSession findByUserIdAndActive(String userId, boolean active);
}