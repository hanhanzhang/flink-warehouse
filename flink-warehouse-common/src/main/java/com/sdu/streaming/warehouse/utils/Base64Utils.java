package com.sdu.streaming.warehouse.utils;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Base64;

public class Base64Utils {

    private static final String DEFAULT_CHARSET = "utf-8";

    private Base64Utils() {

    }

    public static String decode(String cipher) throws IOException {
        if (cipher == null || cipher.isEmpty()) {
            throw new IOException("cipher empty");
        }
        byte[] bytes = Base64.getDecoder().decode(cipher);
        return new String(bytes, Charset.forName(DEFAULT_CHARSET));
    }

    public static String encode(String plainText) throws IOException {
        if (plainText == null || plainText.isEmpty()) {
            throw new IOException("plain text empty");
        }
        return Base64.getEncoder().encodeToString(plainText.getBytes(Charset.forName(DEFAULT_CHARSET)));
    }
}
