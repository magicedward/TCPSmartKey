package com.erobbing.sdk.utils;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 *
 */
public class TimeChange {
    /**
     * 杞崲鍒锋柊鏃堕棿鏍煎紡
     */
    public static String getCurrentTime(long time) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date date = new Date(time);
        return sdf.format(date);

    }

    public static String getSeconds() {
        SimpleDateFormat formatter = new SimpleDateFormat("HH:mm:ss");
        Date currentTime = new Date();
        String dateString = formatter.format(currentTime);
        return dateString;
    }

    //璁剧疆鏃堕棿
    public static String setTime(long total) {
        long hour = total / (1000 * 60 * 60);
        long letf1 = total % (1000 * 60 * 60);
        long minute = letf1 / (1000 * 60);
        long left2 = letf1 % (1000 * 60);
        long second = left2 / 1000;

        return hour + "'" + minute + "'" + second + "''";
    }

    /**
     * byte(瀛楄妭)鏍规嵁闀垮害杞垚kb(鍗冨瓧鑺�)鍜宮b(鍏嗗瓧鑺�)
     *
     * @param bytes
     * @return
     */
    public static String bytes2kb(long bytes) {
        BigDecimal filesize = new BigDecimal(bytes);
        BigDecimal megabyte = new BigDecimal(1024 * 1024);
        float returnValue = filesize.divide(megabyte, 2, BigDecimal.ROUND_UP)
                .floatValue();
        if (returnValue > 1)
            return (returnValue + "MB");
        BigDecimal kilobyte = new BigDecimal(1024);
        returnValue = filesize.divide(kilobyte, 2, BigDecimal.ROUND_UP)
                .floatValue();
        return (returnValue + "KB");
    }

    /**
     * string绫诲瀷杞崲涓篸ate绫诲瀷
     *
     * @param strTime    瑕佽浆鎹㈢殑string绫诲瀷鐨勬椂闂达紝strTime鐨勬椂闂存牸寮忓繀椤昏涓巉ormatType鐨勬椂闂存牸寮忕浉鍚�
     * @param formatType formatType瑕佽浆鎹㈢殑鏍煎紡yyyy-MM-dd HH:mm:ss//yyyy骞碝M鏈坉d鏃�
     * @return
     * @throws ParseException
     */
    public static Date stringToDate(String strTime, String formatType)
            throws ParseException {
        SimpleDateFormat formatter = new SimpleDateFormat(formatType);
        Date date = null;
        date = formatter.parse(strTime);
        return date;
    }

    /**
     * @param data       ate绫诲瀷鐨勬椂闂�
     * @param formatType 鏍煎紡涓簓yyy-MM-dd HH:mm:ss//yyyy骞碝M鏈坉d鏃� HH鏃秏m鍒唖s绉�
     * @return
     */
    public static String dateToString(Date data, String formatType) {
        return new SimpleDateFormat(formatType).format(data);
    }
}
