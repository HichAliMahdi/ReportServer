package com.reportserver.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

public class TotpWebAuthenticationDetails extends WebAuthenticationDetails {

    private final String totpCode;

    public TotpWebAuthenticationDetails(HttpServletRequest request) {
        super(request);
        this.totpCode = request.getParameter("totpCode");
    }

    public String getTotpCode() {
        return totpCode;
    }
}
