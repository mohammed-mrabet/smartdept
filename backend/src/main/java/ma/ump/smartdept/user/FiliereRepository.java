package ma.ump.smartdept.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FiliereRepository extends JpaRepository<Filiere, Long> {

    boolean existsByCodeIgnoreCase(String code);

    List<Filiere> findAllByOrderByCodeAsc();
}
