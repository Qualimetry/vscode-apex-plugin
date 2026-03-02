public class BrokenNullCheckCompliant {
    public void properNullCheck(Account acc) {
        if (acc == null) {
            return;
        }
        acc.Name = 'Updated';
    }

    public void notEqualCheck(Contact c) {
        if (c != null) {
            c.Email = 'test@example.com';
        }
    }

    public void normalAssignment(String s) {
        String val = s;
        if (val == null) {
            val = 'default';
        }
    }

    public void multiCondition(Object obj) {
        if (obj != null && obj instanceof Account) {
            System.debug(obj);
        }
    }
}
