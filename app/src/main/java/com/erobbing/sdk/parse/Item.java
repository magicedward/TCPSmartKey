package com.erobbing.sdk.parse;


/**
 * author: quhuabo, created on 2016/8/25 0029.
 * 瑙ｆ瀽缁勫悎鎶ユ枃涓殑鏌愪竴椤�
 */

import com.erobbing.sdk.model.Result;
import com.erobbing.sdk.model.ResultStage;
import com.erobbing.sdk.utils.HexTools;
import com.erobbing.sdk.utils.MyLogger;

import org.apache.commons.lang3.ArrayUtils;

import java.util.Arrays;

public class Item extends Result implements Parser {
    private MyLogger mLog = MyLogger.jLog();
    private int mNeedLen = 0;
    private byte[] mMatchValue = null;

    /**
     * 闇�瑕佺殑闀垮害锛岄渶瑕佸尮閰嶇殑鍊�
     *
     * @param matchValue锛屽綋涓� NULL 鏃朵笉闇�瑕佸尮閰�
     */
    public Item(byte[] matchValue) {
        mMatchValue = matchValue;
        setNeedLen(matchValue.length);
    }

    public Item(int len) {
        setNeedLen(len);
    }

    public int getNeedLen() {
        return mNeedLen;
    }

    public void setNeedLen(int mNeedLen) {
        this.mNeedLen = mNeedLen;
    }

    /**
     * 瑙ｆ瀽 buff
     *
     * @param buff
     * @return 鍓╀綑鐨勬暟缁�
     */
    public byte[] parse(byte[] buff) {
        byte[] ret = null;
        int nWantLen = getNeedLen() - get_recogized_len();
        if (buff.length < nWantLen) {
            addBuffer(buff, buff.length);
            ret = null;
        } else {
            if (nWantLen > 0) {
                addBuffer(buff, nWantLen);//set recognized
                ret = ArrayUtils.subarray(buff, nWantLen, buff.length);
                if (mMatchValue != null) {
                    if (Arrays.equals(get_recognized(), mMatchValue)) {
                        setStage(ResultStage.Ok);
                    } else {
                        mLog.w("parse failed, expected: " + HexTools.byteArrayToHex(mMatchValue) + ", but got: " + HexTools.byteArrayToHex(get_recognized()));
                        setStage(ResultStage.Fail);
                    }
                } else {
                    setStage(ResultStage.Ok);
                }
            } else if (nWantLen == 0) {
                ret = buff;
                setStage(ResultStage.Ok);
            }
        }
        if (ret != null && ret.length == 0) {
            ret = null;
        }
        return ret;
    }

}