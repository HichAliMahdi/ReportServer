package com.reportserver.service;

import com.reportserver.model.InstallationState;
import com.reportserver.repository.InstallationStateRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class InstallationService {

    private final InstallationStateRepository installationStateRepository;

    @Value("${reportserver.installation.enabled:true}")
    private boolean installationEnabled;

    public InstallationService(InstallationStateRepository installationStateRepository) {
        this.installationStateRepository = installationStateRepository;
    }

    public boolean isInstallationEnabled() {
        return installationEnabled;
    }

    public boolean isSetupComplete() {
        if (!installationEnabled) {
            return true;
        }
        return installationStateRepository.findTopByOrderByIdAsc()
                .map(InstallationState::isSetupCompleted)
                .orElse(false);
    }

    public Map<String, Object> getSetupStatus() {
        Map<String, Object> status = new LinkedHashMap<>();

        InstallationState state = installationStateRepository.findTopByOrderByIdAsc().orElse(null);
        status.put("installationEnabled", installationEnabled);
        status.put("setupCompleted", state != null && state.isSetupCompleted());
        status.put("adminConfigured", state != null && state.isAdminConfigured());
        status.put("smtpConfigured", state != null && state.isSmtpConfigured());
        status.put("completedAt", state != null ? state.getSetupCompletedAt() : null);
        return status;
    }

    public void markAdminConfigured() {
        InstallationState state = getOrCreateState();
        state.setAdminConfigured(true);
        installationStateRepository.save(state);
    }

    public void markSmtpConfigured() {
        InstallationState state = getOrCreateState();
        state.setSmtpConfigured(true);
        installationStateRepository.save(state);
    }

    public void markSetupCompleted() {
        InstallationState state = getOrCreateState();
        if (!state.isSmtpConfigured()) {
            throw new IllegalStateException("SMTP setup must be completed before finalizing installation");
        }
        if (!state.isAdminConfigured()) {
            throw new IllegalStateException("Admin setup must be completed before finalizing installation");
        }
        state.setSetupCompleted(true);
        state.setSetupCompletedAt(LocalDateTime.now());
        installationStateRepository.save(state);
    }

    private InstallationState getOrCreateState() {
        return installationStateRepository.findTopByOrderByIdAsc().orElseGet(InstallationState::new);
    }
}
