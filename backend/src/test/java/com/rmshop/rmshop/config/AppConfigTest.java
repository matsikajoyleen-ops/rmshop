package com.rmshop.rmshop.config;

import com.rmshop.rmshop.mail.AppsScriptEmailSender;
import com.rmshop.rmshop.mail.BrevoEmailSender;
import com.rmshop.rmshop.mail.LoggingEmailSender;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// White-box: which email service is picked for each combination of settings.
class AppConfigTest {

    private static final String URL = "https://script.google.com/macros/s/abc/exec";

    @Test
    void gmailRelayWinsWhenConfigured() {
        assertThat(AppConfig.chooseSender(URL, "secret", "xkeysib-1", "a@b.com", "RMShop"))
                .isInstanceOf(AppsScriptEmailSender.class);
    }

    @Test
    void relayNeedsBothUrlAndSecret() {
        assertThat(AppConfig.chooseSender(URL, "", "", "", "RMShop")).isInstanceOf(LoggingEmailSender.class);
        assertThat(AppConfig.chooseSender("", "secret", "", "", "RMShop")).isInstanceOf(LoggingEmailSender.class);
    }

    @Test
    void brevoIsUsedWithoutTheRelay() {
        assertThat(AppConfig.chooseSender("", "", "xkeysib-1", "a@b.com", "RMShop"))
                .isInstanceOf(BrevoEmailSender.class);
    }

    @Test
    void nothingConfiguredFallsBackToTheLog() {
        assertThat(AppConfig.chooseSender("", "", "", "", "RMShop")).isInstanceOf(LoggingEmailSender.class);
    }
}
