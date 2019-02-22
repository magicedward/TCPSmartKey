package com.erobbing.tcpsmartkey.util;

public class HexStringUtils {

    private static final char[] DIGITS_HEX = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    protected static char[] encodeHex(byte[] data) {
        int l = data.length;
        char[] out = new char[l << 1];
        for (int i = 0, j = 0; i < l; i++) {
            out[j++] = DIGITS_HEX[(0xF0 & data[i]) >>> 4];
            out[j++] = DIGITS_HEX[0x0F & data[i]];
        }
        return out;
    }

    protected static byte[] decodeHex(char[] data) {
        int len = data.length;
        if ((len & 0x01) != 0) {
            throw new RuntimeException("字符个数应该为偶数");
        }
        byte[] out = new byte[len >> 1];
        for (int i = 0, j = 0; j < len; i++) {
            int f = toDigit(data[j], j) << 4;
            j++;
            f |= toDigit(data[j], j);
            j++;
            out[i] = (byte) (f & 0xFF);
        }
        return out;
    }

    protected static int toDigit(char ch, int index) {
        int digit = Character.digit(ch, 16);
        if (digit == -1) {
            throw new RuntimeException("Illegal hexadecimal character " + ch + " at index " + index);
        }
        return digit;
    }

    public static String toHexString(byte[] bs) {
        return new String(encodeHex(bs));
    }

    public static String hexString2Bytes(String hex) {
        return new String(decodeHex(hex.toCharArray()));
    }

    public static byte[] hexStringToBytes(String hex) {
        return decodeHex(hex.toCharArray());
    }

    public static byte[] chars2Bytes(char[] bs) {
        return decodeHex(bs);
    }

    /*public static void main(String[] args) {
        String s = "abc你好";
        String hex = toHexString(s.getBytes());
        String decode = hexString2Bytes(hex);
        System.out.println("原字符串:" + s);
        System.out.println("十六进制字符串:" + hex);
        System.out.println("还原:" + decode);
    }*/

    public static String intToHexStringProvinceAndCity(int num) {
        //String str = Integer.toHexString(in);
        return String.format("%04x", num);
    }

    public static String convertASCIIHexToString(String hex) {
        StringBuilder sb = new StringBuilder();
        StringBuilder temp = new StringBuilder();
        for (int i = 0; i < hex.length() - 1; i += 2) {
            String output = hex.substring(i, (i + 2));
            int decimal = Integer.parseInt(output, 16);
            sb.append((char) decimal);
            temp.append(decimal);
        }
        return sb.toString();
    }

    public String convertStringToASCIIHex(String str) {
        char[] chars = str.toCharArray();
        StringBuffer hex = new StringBuffer();
        for (int i = 0; i < chars.length; i++) {
            hex.append(Integer.toHexString((int) chars[i]));
        }
        return hex.toString();
    }
}