package ma.ump.smartdept.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface UserTokenRepository extends JpaRepository<UserToken, Long> {

    @Query("select t from UserToken t join fetch t.user where t.tokenHash = :hash")
    Optional<UserToken> findByHash(@Param("hash") String hash);

    /** Invalide les jetons encore valides d'un type (un seul lien actif à la fois). */
    @Modifying
    @Query("update UserToken t set t.usedAt = :now where t.user.id = :userId and t.type = :type and t.usedAt is null")
    int invalidate(@Param("userId") Long userId, @Param("type") UserToken.Type type, @Param("now") LocalDateTime now);
}
