package org.example.facade;

import org.example.scheduler.IJobManager;


public class SystemFacade {

    private final IJobManager jobManager;

    public SystemFacade(IJobManager jobManager) {
        this.jobManager = jobManager;
    }

    public void startAnalysis(String filePath) {
        System.out.println("[Facade] Solicitud recibida: Analizar " + filePath);
        jobManager.scheduleJob(filePath);
    }

}
