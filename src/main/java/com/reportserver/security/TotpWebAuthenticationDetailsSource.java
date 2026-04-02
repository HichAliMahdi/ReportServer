package com.reportserver.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AuthenticationDetailsSource;
import org.springframework.stereotype.Component;

@Component
public class TotpWebAuthenticationDetailsSource implements AuthenticationDetailsSource<HttpServletRequest, TotpWebAuthenticationDetails> {

    @Override
    public TotpWebAuthenticationDetails buildDetails(HttpServletRequest context) {
        return new TotpWebAuthenticationDetails(context);
    }
}
