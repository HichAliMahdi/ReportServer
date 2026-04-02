package com.reportserver.security;

import com.reportserver.model.User;
import com.reportserver.repository.UserRepository;
import com.reportserver.service.TwoFactorAuthService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Map;

public class TotpAuthenticationProvider extends DaoAuthenticationProvider {

    private final UserRepository userRepository;
    private final TwoFactorAuthService twoFactorAuthService;

    public TotpAuthenticationProvider(UserRepository userRepository, TwoFactorAuthService twoFactorAuthService) {
        this.userRepository = userRepository;
        this.twoFactorAuthService = twoFactorAuthService;
    }

    @Override
    protected void additionalAuthenticationChecks(UserDetails userDetails,
                                                  UsernamePasswordAuthenticationToken authentication) {
        super.additionalAuthenticationChecks(userDetails, authentication);

        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        if (!user.isTwoFactorEnabled()) {
            return;
        }

        String submittedCode = extractTotpCode(authentication.getDetails());
        if (submittedCode == null || submittedCode.isBlank()) {
            throw new BadCredentialsException("Two-factor authentication code is required");
        }

        boolean valid = twoFactorAuthService.verifyCode(user.getTwoFactorSecret(), submittedCode);
        if (!valid) {
            throw new BadCredentialsException("Invalid two-factor authentication code");
        }

        // First successful TOTP can finalize enrollment.
        if (!user.isTwoFactorConfirmed()) {
            user.setTwoFactorConfirmed(true);
            userRepository.save(user);
        }
    }

    private String extractTotpCode(Object details) {
        if (details instanceof TotpWebAuthenticationDetails webDetails) {
            return webDetails.getTotpCode();
        }

        if (details instanceof Map<?, ?> mapDetails) {
            Object code = mapDetails.get("totpCode");
            return code == null ? null : String.valueOf(code);
        }

        return null;
    }
}
