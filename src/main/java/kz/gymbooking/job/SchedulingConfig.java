package kz.gymbooking.job;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true")
public class SchedulingConfig {

    private final StatusJobs jobs;

    public SchedulingConfig(StatusJobs jobs) {
        this.jobs = jobs;
    }

    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void finishEndedClasses() {
        jobs.finishEndedClasses();
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "${app.time-zone}")
    public void expireMemberships() {
        jobs.expireMemberships();
    }
}
