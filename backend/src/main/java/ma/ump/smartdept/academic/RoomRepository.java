package ma.ump.smartdept.academic;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoomRepository extends JpaRepository<Room, Long> {

    List<Room> findAllByOrderByCodeAsc();

    List<Room> findByActiveTrueOrderByCapacityDesc();

    boolean existsByCodeIgnoreCase(String code);
}
