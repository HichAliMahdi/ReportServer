package com.reportserver.config;

import com.reportserver.service.InstallationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.util.Locale;

@Configuration
public class LocaleConfig implements WebMvcConfigurer {

    private static final String LOCALE_SESSION_ATTRIBUTE = "REPORTSERVER_LOCALE";
    private static final String ORIGINAL_LOCALE_SESSION_ATTRIBUTE = "org.springframework.web.servlet.i18n.SessionLocaleResolver.LOCALE";

    @Bean
    public LocaleResolver localeResolver(InstallationService installationService) {
        return new LocaleResolver() {
            @Override
            public Locale resolveLocale(HttpServletRequest request) {
                // 1. Check both session attributes (for compatibility)
                HttpSession session = request.getSession(false);
                if (session != null) {
                    // Check our custom attribute first
                    Object localeValue = session.getAttribute(LOCALE_SESSION_ATTRIBUTE);
                    if (localeValue instanceof Locale locale) {
                        return locale;
                    }
                    // Check Spring's default session attribute
                    Object springLocale = session.getAttribute(ORIGINAL_LOCALE_SESSION_ATTRIBUTE);
                    if (springLocale instanceof Locale locale) {
                        return locale;
                    }
                }

                // 2. Check URL parameter directly (as fallback)
                String langParam = request.getParameter("lang");
                if (langParam != null && !langParam.isEmpty()) {
                    Locale paramLocale = toLocale(langParam);
                    if (isSupported(paramLocale)) {
                        return paramLocale;
                    }
                }

                // 3. Check installed default
                Locale installedDefault = toLocale(installationService.getDefaultLanguage());
                if (isSupported(installedDefault)) {
                    return installedDefault;
                }

                // 4. Check request locale (browser preference)
                Locale requestLocale = request.getLocale();
                if (isSupported(requestLocale)) {
                    return requestLocale;
                }

                // 5. Default to English
                return Locale.ENGLISH;
            }

            @Override
            public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
                HttpSession session = request.getSession(true);
                Locale supportedLocale = toSupportedLocale(locale);
                // Store in both our attribute and Spring's default attribute for compatibility
                session.setAttribute(LOCALE_SESSION_ATTRIBUTE, supportedLocale);
                session.setAttribute(ORIGINAL_LOCALE_SESSION_ATTRIBUTE, supportedLocale);
            }
        };
    }

    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName("lang");
        return interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Register LocaleChangeInterceptor first with highest priority (low order)
        registry.addInterceptor(localeChangeInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns("/api/**", "/static/**")
                .order(0);
    }

    private boolean isSupported(Locale locale) {
        if (locale == null) {
            return false;
        }
        String language = locale.getLanguage();
        return "en".equals(language) || "fr".equals(language) || "de".equals(language);
    }

    private Locale toLocale(String language) {
        if ("fr".equalsIgnoreCase(language)) {
            return Locale.FRENCH;
        }
        if ("de".equalsIgnoreCase(language)) {
            return Locale.GERMAN;
        }
        return Locale.ENGLISH;
    }

    private Locale toSupportedLocale(Locale locale) {
        if (locale == null) {
            return Locale.ENGLISH;
        }
        if ("fr".equals(locale.getLanguage())) {
            return Locale.FRENCH;
        }
        if ("de".equals(locale.getLanguage())) {
            return Locale.GERMAN;
        }
        return Locale.ENGLISH;
    }
}
