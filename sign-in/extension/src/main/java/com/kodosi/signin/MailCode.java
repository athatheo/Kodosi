package com.kodosi.signin;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * The mail codes of one sign-in. The codes live in the notes of the sign-in that asked for them, each
 * under the address that it went to, and the limits of a mail address live in a store that all sign-ins
 * share.
 */
final class MailCode {
    static final int DIGITS = 8;
    static final int RESEND_SECONDS = 60;
    private static final int KEPT = 3;
    private static final int WRONG_PER_SIGN_IN = 5;
    private static final int MAILS_PER_HOUR = 5;
    private static final int WRONG_PER_DAY = 20;
    private static final int HOUR = 3600;
    private static final int DAY = 86400;

    private static final String CODES = "kodosi.code.codes";
    private static final String SENT = "kodosi.code.sent";
    private static final String LAST = "kodosi.code.last";
    private static final String WRONG = "kodosi.code.wrong";

    private static final SecureRandom RANDOM = new SecureRandom();

    interface Notes {
        String get(String name);

        void set(String name, String value);

        void remove(String name);
    }

    /** Keys that one caller at a time can hold, each for a time. */
    interface Store {
        boolean hold(String key, int seconds);

        void drop(String key);
    }

    /** What the page says about a request for a mail. */
    enum Shown { SENT, WAIT, LATER }

    /** The mail that goes out: none, a code, or a note that the address has an account. */
    enum Mail { NONE, CODE, ACCOUNT }

    record Send(Shown shown, Mail mail, String code) {}

    enum Check { PASSED, WRONG, ENDED, OLD }

    private final Notes notes;
    private final Store store;
    private final long now;
    private final int life;

    MailCode(Notes notes, Store store, long now, int life) {
        this.notes = notes;
        this.store = store;
        this.now = now;
        this.life = life;
    }

    /**
     * Asks for a mail to {@code address}, or to nobody when it is null. When {@code codeWorks} is false, as
     * for an address that has an account, the mail says so in place of a code.
     */
    Send send(String address, boolean codeWorks) {
        if (waitSeconds() > 0) return new Send(Shown.WAIT, Mail.NONE, null);
        List<Long> sent = sent();
        if (sent.size() >= MAILS_PER_HOUR) return new Send(Shown.LATER, Mail.NONE, null);
        sent.add(now);
        notes.set(SENT, join(sent));
        notes.set(LAST, Long.toString(now));
        if (address == null || hold("mail", address, MAILS_PER_HOUR, HOUR) == null) {
            return new Send(Shown.SENT, Mail.NONE, null);
        }
        if (!codeWorks) return new Send(Shown.SENT, Mail.ACCOUNT, null);
        String code = String.format("%0" + DIGITS + "d", RANDOM.nextInt((int) Math.pow(10, DIGITS)));
        List<String> codes = new ArrayList<>(codes(address));
        codes.add(code + "@" + now);
        notes.set(codesOf(address), String.join(",", codes.subList(Math.max(0, codes.size() - KEPT), codes.size())));
        return new Send(Shown.SENT, Mail.CODE, code);
    }

    /**
     * Checks a typed code against the codes sent to {@code address}, or to nobody when it is null. A code
     * that is not right counts, and from the fifth of a sign-in on, each one ends the codes sent.
     */
    Check check(String typed, String address) {
        String digits = typed == null ? "" : typed.replaceAll("[\\s-]", "");
        if (!digits.matches("[0-9]{" + DIGITS + "}")) return Check.WRONG;
        String held = address == null ? null : hold("wrong", address, WRONG_PER_DAY, DAY);
        if (held != null) {
            for (String entry : codes(address)) {
                int at = entry.indexOf('@');
                if (!MessageDigest.isEqual(entry.substring(0, at).getBytes(UTF_8), digits.getBytes(UTF_8))) continue;
                store.drop(held);
                if (number(entry.substring(at + 1)) + life <= now) return Check.OLD;
                notes.remove(codesOf(address));
                return Check.PASSED;
            }
        }
        long wrong = number(notes.get(WRONG)) + 1;
        notes.set(WRONG, Long.toString(wrong));
        if (wrong < WRONG_PER_SIGN_IN) return Check.WRONG;
        if (address != null) notes.remove(codesOf(address));
        return Check.ENDED;
    }

    /** Drops the wait of the address before, when the person gives a different one. */
    void forget() {
        notes.remove(LAST);
    }

    int waitSeconds() {
        String last = notes.get(LAST);
        return last == null ? 0 : (int) Math.max(0, number(last) + RESEND_SECONDS - now);
    }

    boolean tooOften() {
        return sent().size() >= MAILS_PER_HOUR;
    }

    private List<String> codes(String address) {
        String codes = notes.get(codesOf(address));
        return codes == null || codes.isEmpty() ? List.of() : List.of(codes.split(","));
    }

    private static String codesOf(String address) {
        return CODES + "." + key(address);
    }

    private List<Long> sent() {
        String sent = notes.get(SENT);
        List<Long> times = new ArrayList<>();
        if (sent == null || sent.isEmpty()) return times;
        Arrays.stream(sent.split(",")).map(MailCode::number).filter(time -> time > now - HOUR).forEach(times::add);
        return times;
    }

    private String hold(String kind, String address, int limit, int seconds) {
        String prefix = "kodosi-mail-code." + kind + "." + key(address) + ".";
        for (int slot = 0; slot < limit; slot++) {
            if (store.hold(prefix + slot, seconds)) return prefix + slot;
        }
        return null;
    }

    private static String key(String address) {
        try {
            byte[] text = address.toLowerCase(Locale.ROOT).getBytes(UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static long number(String text) {
        return text == null ? 0 : Long.parseLong(text);
    }

    private static String join(List<Long> times) {
        return String.join(",", times.stream().map(String::valueOf).toList());
    }
}
