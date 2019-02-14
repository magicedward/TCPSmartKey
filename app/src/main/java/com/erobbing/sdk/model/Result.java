package com.erobbing.sdk.model;

import org.apache.commons.lang3.ArrayUtils;

public class Result {
    private byte[] recognized = null;

    private ResultStage succeeded = ResultStage.cached;

    public Result() {

    }

    private Result(byte[] recognized, ResultStage succeeded) {
        this.recognized = recognized;
        this.succeeded = succeeded;
    }

    /**
     * 鏄惁瑙ｆ瀽鎴愬姛
     *
     * @return
     */
    public boolean isParseOk() {
        return succeeded == ResultStage.Ok;
    }

    /**
     * 鑾峰彇宸茬粡鎺ュ彈鐨勫唴瀹�
     */
    public byte[] get_recognized() {
        return recognized;
    }

    public int get_recogized_len() {
        return recognized == null ? 0 : recognized.length;
    }


    /**
     * 鍔犲叆缂撳瓨
     */
    public void addBuffer(byte[] buff, int len) {
        recognized = ArrayUtils.addAll(recognized, ArrayUtils.subarray(buff, 0, len));
    }

    public ResultStage getStage() {
        return succeeded;
    }

    public void setStage(ResultStage val) {
        succeeded = val;
    }

    public void reset() {
        recognized = null;
        succeeded = ResultStage.cached;
    }

    ;
}