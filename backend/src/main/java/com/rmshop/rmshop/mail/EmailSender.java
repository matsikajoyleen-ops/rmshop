package com.rmshop.rmshop.mail;

public interface EmailSender {

    /** Sends one email. Throws {@link EmailDeliveryException} if it could not be handed off. */
    void send(String to, String subject, String textBody, String htmlBody);

    class EmailDeliveryException extends RuntimeException {
        public EmailDeliveryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
