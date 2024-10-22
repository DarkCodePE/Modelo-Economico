package ms.hispam.budget.repository.mysql;

import ms.hispam.budget.entity.mysql.ProjectionHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectionHistoryRepository extends JpaRepository<ProjectionHistory, Long> {
    List<ProjectionHistory> findByUserId(Long userId);
    List<ProjectionHistory> findByHash(String hash);
    boolean existsByHash(String hash);
    // Método para obtener la versión máxima para un hash dado
    @Query("SELECT MAX(p.version) FROM ProjectionHistory p WHERE p.hash = :hash")
    Integer findMaxVersionByHash(@Param("hash") String hash);
    // Método para encontrar si existe una proyección con el mismo hash y version
    Optional<ProjectionHistory> findByHashAndVersion(String hash, int version);
    // Método para obtener todas las proyecciones por hash
    List<ProjectionHistory> findByHashOrderByVersionDesc(String hash);
}
