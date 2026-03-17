package com.reportserver.security;

import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SessionCleanupListener implements HttpSessionListener {

    @Autowired
    private UserSessionRegistry userSessionRegistry;

    @Override
    public void sessionDestroyed(HttpSessionEvent se) {
        userSessionRegistry.unregisterSession(se.getSession().getId());
    }
}
