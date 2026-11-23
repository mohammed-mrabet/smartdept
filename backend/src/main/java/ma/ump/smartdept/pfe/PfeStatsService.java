package ma.ump.smartdept.pfe;

import ma.ump.smartdept.pfe.PfeDtos.PfeStats;
import ma.ump.smartdept.user.Role;
import ma.ump.smartdept.user.User;
import ma.ump.smartdept.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Indicateurs du tableau de bord PFE (chef de département). */
@Service
@Transactional(readOnly = true)
public class PfeStatsService {

    private final PfeSubjectRepository subjectRepository;
    private final PfeApplicationRepository applicationRepository;
    private final PfeProjectRepository projectRepository;
    private final PfeMilestoneRepository milestoneRepository;
    private final PfeDefenseRepository defenseRepository;
    private final InternshipConventionRepository conventionRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public PfeStatsService(PfeSubjectRepository subjectRepository, PfeApplicationRepository applicationRepository,
                           PfeProjectRepository projectRepository, PfeMilestoneRepository milestoneRepository,
                           PfeDefenseRepository defenseRepository, InternshipConventionRepository conventionRepository,
                           UserRepository userRepository, Clock clock) {
        this.subjectRepository = subjectRepository;
        this.applicationRepository = applicationRepository;
        this.projectRepository = projectRepository;
        this.milestoneRepository = milestoneRepository;
        this.defenseRepository = defenseRepository;
        this.conventionRepository = conventionRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    public PfeStats stats() {
        Map<SubjectStatus, Long> subjects = new EnumMap<>(SubjectStatus.class);
        for (SubjectStatus s : SubjectStatus.values()) {
            subjects.put(s, subjectRepository.countByStatus(s));
        }
        Map<ProjectStatus, Long> projects = new EnumMap<>(ProjectStatus.class);
        for (ProjectStatus s : ProjectStatus.values()) {
            projects.put(s, projectRepository.countByStatus(s));
        }
        List<User> students = userRepository.findByRoleAndEnabledTrueOrderByLastNameAsc(Role.ETUDIANT);
        long withProject = students.stream().filter(s -> projectRepository.existsByStudentId(s.getId())).count();
        LocalDate today = LocalDate.now(clock);
        long late = milestoneRepository.findByStatusInAndDueDateBetween(
                List.of(MilestoneStatus.TODO, MilestoneStatus.NEEDS_REVISION), LocalDate.of(2000, 1, 1), today.minusDays(1)).size();
        Double avg = defenseRepository.averageGrade(DefenseStatus.DONE);
        return new PfeStats(subjects, students.size(), withProject, students.size() - withProject,
                applicationRepository.countByStatus(ApplicationStatus.PENDING), projects, late,
                milestoneRepository.countByStatus(MilestoneStatus.SUBMITTED),
                defenseRepository.countByStatus(DefenseStatus.SCHEDULED), defenseRepository.countByStatus(DefenseStatus.DONE),
                avg == null ? null : Math.round(avg * 100) / 100.0,
                conventionRepository.countByStatus(ConventionStatus.SUBMITTED));
    }
}
