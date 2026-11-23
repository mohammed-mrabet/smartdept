package ma.ump.smartdept.user;

import ma.ump.smartdept.common.ApiException;
import ma.ump.smartdept.user.UserDtos.FiliereRequest;
import ma.ump.smartdept.user.UserDtos.FiliereResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class FiliereService {

    private final FiliereRepository filiereRepository;

    public FiliereService(FiliereRepository filiereRepository) {
        this.filiereRepository = filiereRepository;
    }

    @Transactional(readOnly = true)
    @org.springframework.cache.annotation.Cacheable(ma.ump.smartdept.config.CacheConfig.FILIERES)
    public List<FiliereResponse> list() {
        return filiereRepository.findAllByOrderByCodeAsc().stream().map(FiliereResponse::of).toList();
    }

    @org.springframework.cache.annotation.CacheEvict(value = ma.ump.smartdept.config.CacheConfig.FILIERES, allEntries = true)
    public FiliereResponse create(FiliereRequest r) {
        String code = r.code().trim().toUpperCase();
        if (filiereRepository.existsByCodeIgnoreCase(code)) {
            throw ApiException.conflict("La filière " + code + " existe déjà");
        }
        Filiere f = new Filiere();
        f.setCode(code);
        apply(f, r);
        return FiliereResponse.of(filiereRepository.save(f));
    }

    @org.springframework.cache.annotation.CacheEvict(value = ma.ump.smartdept.config.CacheConfig.FILIERES, allEntries = true)
    public FiliereResponse update(Long id, FiliereRequest r) {
        Filiere f = filiereRepository.findById(id).orElseThrow(() -> ApiException.notFound("Filière introuvable : " + id));
        String code = r.code().trim().toUpperCase();
        if (!code.equalsIgnoreCase(f.getCode()) && filiereRepository.existsByCodeIgnoreCase(code)) {
            throw ApiException.conflict("La filière " + code + " existe déjà");
        }
        f.setCode(code);
        apply(f, r);
        return FiliereResponse.of(filiereRepository.save(f));
    }

    private static void apply(Filiere f, FiliereRequest r) {
        f.setName(r.name().trim());
        f.setLevel(r.level().trim().toUpperCase());
        f.setDescription(r.description() == null || r.description().isBlank() ? null : r.description().trim());
        f.setStudyLevel(r.studyLevel());
    }
}
