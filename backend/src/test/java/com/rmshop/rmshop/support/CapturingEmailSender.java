package com.rmshop.rmshop.support;

import com.rmshop.rmshop.mail.EmailSender;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Keeps sent emails in memory so tests can read the confirmation code, like a manager reading their inbox. */
public class CapturingEmailSender implements EmailSender {

    public record SentEmail(String to, String subject, String text) {}

    private static final Pattern CODE = Pattern.compile("confirmation code is: (\\d{6})");

    private final List<SentEmail> sent = new CopyOnWriteArrayList<>();
    private volatile boolean failNext;

    @Override
    public void send(String to, String subject, String textBody, String htmlBody) {
        if (failNext) {
            failNext = false;
            throw new EmailDeliveryException("simulated outage", null);
        }
        sent.add(new SentEmail(to, subject, textBody));
    }

    public void clear() {
        sent.clear();
        failNext = false;
    }

    public void failNextSend() {
        failNext = true;
    }

    public List<SentEmail> sentTo(String address) {
        return sent.stream().filter(e -> e.to().equals(address)).toList();
    }

    /** The code from the most recent reset email sent to this address. */
    public String latestCodeFor(String address) {
        List<SentEmail> emails = sentTo(address);
        for (int i = emails.size() - 1; i >= 0; i--) {
            Matcher m = CODE.matcher(emails.get(i).text());
            if (m.find()) return m.group(1);
        }
        throw new AssertionError("No reset code was emailed to " + address);
    }
}
