/*
 * Copyright 2015 Ellucian Company L.P. and its affiliates.
 */

package com.ellucian.mobile.android.util;

import android.util.Base64;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * Usage:
 * <pre>
 * String crypto = Encrypt.encrypt(masterpassword, cleartext)
 * ...
 * String cleartext = Encrypt.decrypt(masterpassword, crypto)
 * </pre>
 * @author ferenc.hechler
 */


public class Encrypt {

    public static String encrypt(SecretKey key, String cleartext) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encrypted = cipher.doFinal(cleartext.getBytes());
        return toHex(encrypted);
    }

    public static String decrypt(SecretKey key, String encrypted) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, key);
        byte[] decrypted = cipher.doFinal(toByte(encrypted));
        return new String(decrypted);
    }

    /*
    http://android-developers.blogspot.com/2013/02/using-cryptography-to-store-credentials.html
     */
    public static SecretKey generateKey() throws NoSuchAlgorithmException {
        // Generate a 256-bit key
        final int outputKeyLength = 256;

        SecureRandom secureRandom = new SecureRandom();
        // Do *not* seed secureRandom! Automatically seeded from system entropy.
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(outputKeyLength, secureRandom);
        SecretKey key = keyGenerator.generateKey();
        return key;
    }

    public static String legacyDecrypt(String encrypted) throws Exception {
        byte[] rawKey = getRawKey("userMaster".getBytes());
        byte[] enc = toByte(encrypted);
        byte[] result = legacyDecrypt(rawKey, enc);
        return new String(result);
    }

    private static byte[] getRawKey(byte[] seed) throws Exception {
        KeyGenerator kgen = KeyGenerator.getInstance("AES");
        SecureRandom sr = SecureRandom.getInstance("SHA1PRNG", "Crypto");
        sr.setSeed(seed);
        kgen.init(128, sr); // 192 and 256 bits may not be available
        SecretKey skey = kgen.generateKey();
        byte[] raw = skey.getEncoded();
    	return raw;
    }

    private static byte[] legacyDecrypt(byte[] raw, byte[] encrypted) throws Exception {
    	SecretKeySpec skeySpec = new SecretKeySpec(raw, "AES");
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, skeySpec);
    	byte[] decrypted = cipher.doFinal(encrypted);
        return decrypted;
    }


    public static String fromHex(String hex) {
        return new String(toByte(hex));
    }

    private static byte[] toByte(String hexString) {
        int len = hexString.length()/2;
        byte[] result = new byte[len];
        for (int i = 0; i < len; i++)
        	result[i] = Integer.valueOf(hexString.substring(2*i, 2*i+2), 16).byteValue();
        return result;
    }

    public static String keyToString(SecretKey key) {
        return Base64.encodeToString(key.getEncoded(), Base64.DEFAULT);
    }

    public static SecretKey stringToKey(String keyString) {
        byte[] encodedKey = Base64.decode(keyString, Base64.DEFAULT);
        return new SecretKeySpec(encodedKey, 0, encodedKey.length, "AES");
    }

    public static String toHex(byte[] buf) {
        if (buf == null)
        	return "";
        StringBuffer result = new StringBuffer(2*buf.length);
        for (int i = 0; i < buf.length; i++) {
        	appendHex(result, buf[i]);
        }
        return result.toString();
    }
    private final static String HEX = "0123456789ABCDEF";
    private static void appendHex(StringBuffer sb, byte b) {
        sb.append(HEX.charAt((b>>4)&0x0f)).append(HEX.charAt(b&0x0f));
    }

}
