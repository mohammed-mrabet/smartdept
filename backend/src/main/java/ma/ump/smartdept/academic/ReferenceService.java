package ma.ump.smartdept.academic;

import ma.ump.smartdept.academic.AcademicDtos.*;
import ma.ump.smartdept.common.ApiException;
import ma.ump.smartdept.user.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Salles et modules (référentiel géré par le chef de département). */
@Service
@Transactional
public class ReferenceService {

    private final RoomRepository roomRepository;
    private final CourseModuleRepository moduleRepository;
    private final FiliereRepository filiereRepository;
    private final UserRepository userRepository;

    public ReferenceService(RoomRepository roomRepository, CourseModuleRepository moduleRepository,
                            FiliereRepository filiereRepository, UserRepository userRepository) {
        this.roomRepository = roomRepository;
        this.moduleRepository = moduleRepository;
        this.filiereRepository = filiereRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<RoomResponse> rooms() {
        return roomRepository.findAllByOrderByCodeAsc().stream().map(RoomResponse::of).toList();
    }

    public RoomResponse createRoom(RoomRequest r) {
        if (roomRepository.existsByCodeIgnoreCase(r.code().trim())) throw ApiException.conflict("La salle " + r.code() + " existe déjà");
        Room room = new Room();
        applyRoom(room, r);
        return RoomResponse.of(roomRepository.save(room));
    }

    public RoomResponse updateRoom(Long id, RoomRequest r) {
        Room room = room(id);
        if (!room.getCode().equalsIgnoreCase(r.code().trim()) && roomRepository.existsByCodeIgnoreCase(r.code().trim())) {
            throw ApiException.conflict("La salle " + r.code() + " existe déjà");
        }
        applyRoom(room, r);
        return RoomResponse.of(room);
    }

    private static void applyRoom(Room room, RoomRequest r) {
        room.setCode(r.code().trim().toUpperCase());
        room.setName(r.name().trim());
        room.setCapacity(r.capacity());
        room.setType(r.type());
        room.setBuilding(r.building() == null || r.building().isBlank() ? null : r.building().trim());
        room.setActive(r.active() == null || r.active());
    }

    public Room room(Long id) {
        return roomRepository.findById(id).orElseThrow(() -> ApiException.notFound("Salle introuvable : " + id));
    }

    @Transactional(readOnly = true)
    public List<ModuleResponse> modules(Long filiereId, Long teacherId) {
        return moduleRepository.search(filiereId, teacherId).stream().map(ModuleResponse::of).toList();
    }

    public ModuleResponse createModule(ModuleRequest r) {
        if (moduleRepository.existsByCodeIgnoreCase(r.code().trim())) throw ApiException.conflict("Le module " + r.code() + " existe déjà");
        CourseModule m = new CourseModule();
        applyModule(m, r);
        return ModuleResponse.of(moduleRepository.save(m));
    }

    public ModuleResponse updateModule(Long id, ModuleRequest r) {
        CourseModule m = module(id);
        if (!m.getCode().equalsIgnoreCase(r.code().trim()) && moduleRepository.existsByCodeIgnoreCase(r.code().trim())) {
            throw ApiException.conflict("Le module " + r.code() + " existe déjà");
        }
        applyModule(m, r);
        return ModuleResponse.of(m);
    }

    private void applyModule(CourseModule m, ModuleRequest r) {
        Filiere f = filiereRepository.findById(r.filiereId()).orElseThrow(() -> ApiException.notFound("Filière introuvable : " + r.filiereId()));
        User teacher = null;
        if (r.teacherId() != null) {
            teacher = userRepository.findById(r.teacherId()).orElseThrow(() -> ApiException.notFound("Enseignant introuvable : " + r.teacherId()));
            if (teacher.getRole() != Role.ENSEIGNANT && teacher.getRole() != Role.CHEF_DEPARTEMENT) {
                throw ApiException.badRequest(teacher.getFullName() + " n'est pas enseignant");
            }
        }
        m.setCode(r.code().trim().toUpperCase());
        m.setName(r.name().trim());
        m.setFiliere(f);
        m.setSemester(r.semester());
        m.setTeacher(teacher);
        m.setCoefficient(r.coefficient());
        m.setCmPerWeek(r.cmPerWeek());
        m.setTdPerWeek(r.tdPerWeek());
        m.setTpPerWeek(r.tpPerWeek());
        m.setDescription(r.description() == null || r.description().isBlank() ? null : r.description().trim());
    }

    public CourseModule module(Long id) {
        return moduleRepository.findById(id).orElseThrow(() -> ApiException.notFound("Module introuvable : " + id));
    }
}
