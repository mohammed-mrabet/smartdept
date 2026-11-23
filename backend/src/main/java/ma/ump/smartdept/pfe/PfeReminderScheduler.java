package ma.ump.smartdept.pfe;

import ma.ump.smartdept.notification.NotificationRepository;
import ma.ump.smartdept.notification.NotificationService;
import ma.ump.smartdept.notification.NotificationType;
import ma.ump.smartdept.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Rappels automatiques, chaque matin (app.reminders.cron) :
 * - jalons à rendre dans les N prochains jours (app.reminders.days-before) → étudiants du projet ;
 * - soutenances du lendemain → étudiants, encadrant et jury.
 * Un même rappel n'est envoyé qu'une fois par jour.
 */
@Component
public class PfeReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(PfeReminderScheduler.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final PfeMilestoneRepository milestoneRepository;
    private final PfeDefenseRepository defenseRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notifications;
    private final Clock clock;
    private final int daysBefore;

    public PfeReminderScheduler(PfeMilestoneRepository milestoneRepository, PfeDefenseRepository defenseRepository,
                                NotificationRepository notificationRepository, NotificationService notifications,
                                Clock clock, @Value("${app.reminders.days-before:3}") int daysBefore) {
        this.milestoneRepository = milestoneRepository;
        this.defenseRepository = defenseRepository;
        this.notificationRepository = notificationRepository;
        this.notifications = notifications;
        this.clock = clock;
        this.daysBefore = daysBefore;
    }

    @Scheduled(cron = "${app.reminders.cron}")
    @Transactional
    public int sendReminders() {
        LocalDate today = LocalDate.now(clock);
        int sent = 0;
        for (PfeMilestone m : milestoneRepository.findByStatusInAndDueDateBetween(
                List.of(MilestoneStatus.TODO, MilestoneStatus.NEEDS_REVISION), today, today.plusDays(daysBefore))) {
            PfeProject p = m.getProject();
            if (p.getStatus() == ProjectStatus.DEFENDED) {
                continue;
            }
            long days = ChronoUnit.DAYS.between(today, m.getDueDate());
            String link = "/pfe/mon-projet#jalon-" + m.getId();
            for (User student : p.getStudents()) {
                if (!alreadySentToday(student, NotificationType.MILESTONE_DUE, link, today)) {
                    notifications.notify(student, NotificationType.MILESTONE_DUE, "Rappel : « " + m.getTitle() + " »",
                            "Le jalon « " + m.getTitle() + " » est à rendre " + (days == 0 ? "aujourd'hui" : "dans " + days + " jour(s)")
                                    + " (le " + m.getDueDate().format(DATE) + ").", link);
                    sent++;
                }
            }
        }
        LocalDate tomorrow = today.plusDays(1);
        for (PfeDefense d : defenseRepository.findBetween(tomorrow, tomorrow)) {
            if (d.getStatus() != DefenseStatus.SCHEDULED) {
                continue;
            }
            List<User> people = new ArrayList<>(d.getProject().getStudents());
            people.add(d.getProject().getSupervisor());
            people.addAll(d.getJury());
            String link = "/pfe/soutenances#soutenance-" + d.getId();
            for (User u : people) {
                if (!alreadySentToday(u, NotificationType.DEFENSE_SCHEDULED, link, today)) {
                    notifications.notify(u, NotificationType.DEFENSE_SCHEDULED, "Rappel : soutenance demain",
                            "Soutenance du projet « " + d.getProject().getSubject().getTitle() + " » demain à "
                                    + d.getStartTime() + ", salle " + d.getRoom() + ".", link);
                    sent++;
                }
            }
        }
        if (sent > 0) {
            log.info("{} rappel(s) PFE envoyé(s)", sent);
        }
        return sent;
    }

    private boolean alreadySentToday(User u, NotificationType type, String link, LocalDate today) {
        return notificationRepository.existsByRecipientIdAndTypeAndLinkAndCreatedAtAfter(u.getId(), type, link, today.atStartOfDay());
    }
}
