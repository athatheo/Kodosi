package com.kodosi.signin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.kodosi.signin.MailCode.Check;
import com.kodosi.signin.MailCode.Mail;
import com.kodosi.signin.MailCode.Shown;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MailCodeTest {
    private static final int LIFE = 900;
    private static final String MAYA = "maya@kodosi.example";

    private long now = 1_000_000;
    private final Map<String, Long> held = new HashMap<>();
    private final MailCode.Store store = new MailCode.Store() {
        @Override
        public boolean hold(String key, int seconds) {
            Long until = held.get(key);
            if (until != null && until > now) return false;
            held.put(key, now + seconds);
            return true;
        }

        @Override
        public void drop(String key) {
            held.remove(key);
        }
    };

    private static MailCode.Notes signIn() {
        return notesOf(new HashMap<>());
    }

    private static MailCode.Notes notesOf(Map<String, String> notes) {
        return new MailCode.Notes() {
            @Override public String get(String name) { return notes.get(name); }
            @Override public void set(String name, String value) { notes.put(name, value); }
            @Override public void remove(String name) { notes.remove(name); }
        };
    }

    private MailCode in(MailCode.Notes signIn) {
        return new MailCode(signIn, store, now, LIFE);
    }

    private String code(MailCode.Notes signIn) {
        MailCode.Send sent = in(signIn).send(MAYA, true);
        assertEquals(Mail.CODE, sent.mail());
        return sent.code();
    }

    private static String other(String code) {
        return code.equals("00000000") ? "00000001" : "00000000";
    }

    @Test
    void aCodePassesOnceWithOrWithoutSpaces() {
        MailCode.Notes signIn = signIn();
        String code = code(signIn);
        assertEquals(8, code.length());
        assertEquals(Check.PASSED, in(signIn).check(" " + code.substring(0, 4) + " " + code.substring(4) + " ", MAYA));
        assertEquals(Check.WRONG, in(signIn).check(code, MAYA));
    }

    @Test
    void aCodeWorksOnlyInTheSignInThatAskedForIt() {
        String code = code(signIn());
        MailCode.Notes another = signIn();
        code(another);
        assertEquals(Check.WRONG, in(another).check(code, MAYA));
    }

    @Test
    void aCodePassesOnlyForTheAddressThatItWentTo() {
        MailCode.Notes signIn = signIn();
        String code = code(signIn);
        assertEquals(Check.WRONG, in(signIn).check(code, "leo@kodosi.example"));
        assertEquals(Check.PASSED, in(signIn).check(code, MAYA));
    }

    @Test
    void aCodeIsTooOldAfterItsLifeAndThatDoesNotCount() {
        MailCode.Notes signIn = signIn();
        String code = code(signIn);
        now += LIFE;
        assertEquals(Check.OLD, in(signIn).check(code, MAYA));
        for (int i = 0; i < 4; i++) assertEquals(Check.WRONG, in(signIn).check(other(code), MAYA));
        assertEquals(Check.ENDED, in(signIn).check(other(code), MAYA));
    }

    @Test
    void theFifthWrongCodeEndsTheCodesAndANewCodeDoesNotStartTheCountAgain() {
        MailCode.Notes signIn = signIn();
        String first = code(signIn);
        for (int i = 0; i < 4; i++) assertEquals(Check.WRONG, in(signIn).check(other(first), MAYA));
        assertEquals(Check.ENDED, in(signIn).check(other(first), MAYA));
        assertEquals(Check.ENDED, in(signIn).check(first, MAYA));

        now += MailCode.RESEND_SECONDS;
        String second = code(signIn);
        assertEquals(Check.ENDED, in(signIn).check(other(second), MAYA));
        assertEquals(Check.ENDED, in(signIn).check(second, MAYA));

        now += MailCode.RESEND_SECONDS;
        assertEquals(Check.PASSED, in(signIn).check(code(signIn), MAYA));
    }

    @Test
    void aNewCodeWaitsAMinuteAndTheLastThreeCodesWork() {
        MailCode.Notes signIn = signIn();
        List<String> codes = new ArrayList<>();
        codes.add(code(signIn));
        now += 30;
        assertEquals(Shown.WAIT, in(signIn).send(MAYA, true).shown());
        assertEquals(30, in(signIn).waitSeconds());
        for (int i = 0; i < 3; i++) {
            now += MailCode.RESEND_SECONDS;
            codes.add(code(signIn));
        }
        assertEquals(Check.WRONG, in(signIn).check(codes.get(0), MAYA));
        assertEquals(Check.PASSED, in(signIn).check(codes.get(1), MAYA));
    }

    @Test
    void aSignInAsksForAtMostFiveMailsAnHour() {
        MailCode.Notes signIn = signIn();
        long start = now;
        for (int i = 0; i < 5; i++) {
            code(signIn);
            now += MailCode.RESEND_SECONDS;
        }
        assertEquals(Shown.LATER, in(signIn).send(MAYA, true).shown());
        assertEquals(Shown.LATER, in(signIn).send("leo@kodosi.example", true).shown());
        now = start + 3600;
        code(signIn);
    }

    @Test
    void anAddressGetsAtMostFiveMailsAnHourFromAllSignIns() {
        for (int i = 0; i < 5; i++) code(signIn());
        MailCode.Send sixth = in(signIn()).send(MAYA, true);
        assertEquals(Shown.SENT, sixth.shown());
        assertEquals(Mail.NONE, sixth.mail());
        now += 3600;
        code(signIn());
    }

    @Test
    void theWrongCodesOfOtherSignInsNeverStopThePersonsOwnCode() {
        for (int i = 0; i < 4; i++) {
            MailCode.Notes signIn = signIn();
            String code = code(signIn);
            for (int j = 0; j < 25; j++) in(signIn).check(other(code), MAYA);
        }
        MailCode.Notes own = signIn();
        assertEquals(Check.PASSED, in(own).check(code(own), MAYA));
    }

    @Test
    void aSignInComparesAtMostTenCodesAlsoWhenItsPostsComeAtOnce() {
        Map<String, String> notes = new HashMap<>();
        String code = code(notesOf(notes));
        for (int i = 0; i < 10; i++) in(notesOf(new HashMap<>(notes))).check(other(code), MAYA);
        assertEquals(Check.WRONG, in(notesOf(new HashMap<>(notes))).check(code, MAYA));
    }

    @Test
    void aSignInForNoAccountOrForAnAddressWithAnAccountAnswersAsOneThatGetsCodes() {
        MailCode.Notes withCodes = signIn();
        MailCode.Notes noAccount = signIn();
        MailCode.Notes hasAccount = signIn();
        String leo = "leo@kodosi.example";

        MailCode.Send code = in(withCodes).send(MAYA, true);
        MailCode.Send none = in(noAccount).send(null, true);
        MailCode.Send account = in(hasAccount).send(leo, false);
        assertEquals(Shown.SENT, code.shown());
        assertEquals(Shown.SENT, none.shown());
        assertEquals(Shown.SENT, account.shown());
        assertEquals(Mail.NONE, none.mail());
        assertEquals(Mail.ACCOUNT, account.mail());
        assertNull(account.code());

        String guess = other(code.code());
        for (int i = 0; i < 5; i++) {
            Check expected = in(withCodes).check(guess, MAYA);
            assertEquals(expected, in(noAccount).check(guess, null));
            assertEquals(expected, in(hasAccount).check(guess, leo));
        }
        now += 10;
        for (MailCode.Notes signIn : List.of(withCodes, noAccount, hasAccount)) {
            assertEquals(MailCode.RESEND_SECONDS - 10, in(signIn).waitSeconds());
        }
        now += MailCode.RESEND_SECONDS;
        assertEquals(in(withCodes).send(MAYA, true).shown(), in(noAccount).send(null, true).shown());
        assertEquals(Shown.SENT, in(hasAccount).send(leo, false).shown());
    }
}
