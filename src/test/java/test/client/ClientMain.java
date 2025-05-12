package test.client;

import jakarta.annotation.Resource;

import javax.naming.InitialContext;

public class ClientMain {
    @Resource(lookup = "java:app/env/myString")
    private static String myString;

    static void main(String[] args) throws Exception {
        InitialContext ctx = new InitialContext();
        String lookupString = (String) ctx.lookup("java:app/env/myString");
        System.out.println(lookupString);
        if(lookupString == null) {
            throw new IllegalStateException("No java:app/env/myString value");
        }
        if(myString == null) {
            throw new IllegalStateException("No myString field value");
        }
    }
}
