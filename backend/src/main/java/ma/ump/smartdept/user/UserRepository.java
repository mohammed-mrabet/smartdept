package ma.ump.smartdept.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    /** Utilisateur et sa filière en une requête (évite le chargement paresseux hors transaction). */
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.filiere WHERE u.id = :id")
    Optional<User> findWithFiliere(@Param("id") Long id);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByStudentNumber(String studentNumber);

    List<User> findByRoleAndEnabledTrueOrderByLastNameAsc(Role role);

    List<User> findByRoleInAndEnabledTrue(List<Role> roles);

    List<User> findByRoleAndFiliereIdAndEnabledTrue(Role role, Long filiereId);

    @Query("""
            SELECT u FROM User u
            WHERE (:role IS NULL OR u.role = :role)
              AND (:q IS NULL
                   OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%')))
            ORDER BY u.lastName ASC, u.firstName ASC
            """)
    List<User> search(@Param("role") Role role, @Param("q") String q);
}
