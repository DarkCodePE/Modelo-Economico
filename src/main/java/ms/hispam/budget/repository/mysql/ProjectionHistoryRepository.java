package ms.hispam.budget.repository.mysql;

import ms.hispam.budget.entity.mysql.ProjectionHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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
    List<ProjectionHistory> findAllByOrderByCreatedAtDesc();
    /**
     * Encuentra la versión oficial para una BU específica.
     * @param bu La BU para la cual buscar la versión oficial.
     * @return Un Optional que contiene la versión oficial si existe.
     */
    Optional<ProjectionHistory> findByBuAndIsOfficialTrue(String bu);

    /**
     * Desmarca cualquier versión oficial existente para una BU específica.
     * @param bu La BU para la cual desmarcar la versión oficial.
     */
    @Modifying
    @Query("UPDATE ProjectionHistory p SET p.isOfficial = false WHERE p.bu = :bu AND p.isOfficial = true")
    void resetOfficialVersionByBu(String bu);
    /**
     * Encuentra todas las proyecciones que pertenecen al usuario o que son oficiales.
     * @param userId El ID del usuario.
     * @return Una lista de ProjectionHistory que pertenecen al usuario o son oficiales.
     */
    @Query("SELECT p FROM ProjectionHistory p WHERE p.userId = :userId OR p.isOfficial = true ORDER BY p.createdAt DESC")
    List<ProjectionHistory> findUserProjectionsAndOfficialProjections(Long userId);
}
