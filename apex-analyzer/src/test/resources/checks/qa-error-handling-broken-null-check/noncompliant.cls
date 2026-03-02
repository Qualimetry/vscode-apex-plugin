public class BrokenNullCheckNoncompliant {
    public void accidentalAssign(Account acc) {
        if (acc = null) {
            return;
        }
    }

    public void assignMethodResult(Contact c) {
        if (c = getContact()) {
            c.Email = 'test@example.com';
        }
    }

    public void assignVariable(String s) {
        if (s = getValue()) {
            System.debug(s);
        }
    }

    Contact getContact() { return null; }
    String getValue() { return null; }
}
