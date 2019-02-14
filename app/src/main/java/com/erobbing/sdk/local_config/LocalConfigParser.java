package com.erobbing.sdk.local_config;

import android.os.Handler;
import android.os.Looper;

import com.erobbing.sdk.model.Result;
import com.erobbing.sdk.model.ResultStage;
import com.erobbing.sdk.parse.IDiagramParser;
import com.erobbing.sdk.parse.Item;
import com.erobbing.sdk.parse.ParseException;
import com.erobbing.sdk.parse.ParserListener;
import com.erobbing.sdk.utils.ByteUtils;
import com.erobbing.sdk.utils.Crc32;
import com.erobbing.sdk.utils.HexTools;
import com.erobbing.sdk.utils.MyAssert;
import com.erobbing.sdk.utils.MyLogger;

import org.apache.commons.lang3.ArrayUtils;

import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.erobbing.tcpsmartkey.util.BitOperator;
import com.erobbing.tcpsmartkey.util.JT808ProtocolUtils;
import com.erobbing.sdk.model.DiagControl;

/**
 * Created by yinqi on 2017/7/3
 */

public class LocalConfigParser implements IDiagramParser {
    public static final int TAIL_FLAG = 0x7E;
    private static boolean DEBUG = true;


    public static int ITEM_HEAD = 0;
    public static int ITEM_MSGID = 1;
    public static int ITEM_PROP = 2;
    public static int ITEM_PHONENUM = 3;
    public static int ITEM_STREAMNUM = 4;
    public static int ITEM_DATA = 5;
    public static int ITEM_CRC = 6;
    public static int ITEM_TAIL = 7;

    private MyLogger mLog = MyLogger.jLog();

    ArrayList<Item> mItems = new ArrayList<Item>();

    Result mResult = null;
    Item mData = new Item(0);   // 变长 的数据
    private byte[] mBuffer = null;
    int mStep = 0;
    private byte[] mPackData;
    private ParserListener mListener;       // 异步 listener(post 到主线程来执行)
    private ParserListener mSyncListener;   // 同步 listener
    private Handler mHandler = new Handler(Looper.getMainLooper());

    public LocalConfigParser() {
        initial();
    }

    private byte[] getRemain() {
        return mBuffer;
    }

    /*
    7e000200000200000000150003327e
7e # 标识位
000200000200000000150003 # 消息头
    0002 # 消息ID
    0000 # 消息体属性，消息体属性每个位都为零,也即第12-15位的消息包封装项不存在,消息体也为空
    020000000015 # 终端手机号
    0003 # 流水号
    data
32 # 校验码
7e # 标识位
    * */
    private void initial() {
        mItems.add(new Item(new byte[]{0x7e}));  //0. head
        mItems.add(new Item(2)); //1. ID
        mItems.add(new Item(2)); //2. property
        mItems.add(new Item(6)); //3. phone num
        mItems.add(new Item(2)); //4. stream
        mItems.add(new Item(0)); //5. data
        mItems.add(new Item(1)); //6. crc
        mItems.add(new Item(new byte[]{0x7e})); //7.tail
    }

    private int getByteAt(int itemIndex) {
        int ret = 0;
        for (int i = 0; i < itemIndex; ++i) {
            ret += mItems.get(i).getNeedLen();
        }
        return ret;
    }

    private int getDataLen(int itemIndex) {
        return mItems.get(itemIndex).get_recogized_len();
    }

    private boolean isStageOK(List<Item> items) {
        for (Item i : items) {
            if (i.getStage() != ResultStage.Ok) {
                return false;
            }
        }
        return true;
    }

    private int getStep() {
        return mStep;
    }

    private int getStepCount() {
        return mItems.size();
    }


    /**
     * 组合报文解析
     * 会接收多个或不足一个包的数据
     *
     * @param ABuffer
     * @throws ParseException
     */
    public void parse(byte[] ABuffer) throws ParseException {
        if (ABuffer == null) {
            throw new ParseException("buff is empty!");
        }
        MyAssert.Assert(true, ABuffer != this.mBuffer, "DiagramParserZ1.parse");

        this.mBuffer = ArrayUtils.addAll(this.mBuffer, ABuffer);
        if (mBuffer.length < 15) {
            mLog.e("the length is too short, return to wait next process");
            return;
        }

        if (DEBUG) {
            mLog.v("Current buffer data: " + HexTools.byteArrayToHexReadable(mBuffer));
        }

        int start = 0;
        int end = 0;
        byte[] data = null;
        while (true) {
            if (start >= mBuffer.length) {
                mLog.d("no head, discard all data");
                mBuffer = null;
                break;
            }
            while (start < mBuffer.length && mBuffer[start] != TAIL_FLAG) {
                start++;
            }

            //start points to the first 7E
            while ((start + 1) < mBuffer.length && mBuffer[start + 1] == TAIL_FLAG) {
                start++;
            }
            end = start + 1;
            while (end < mBuffer.length && mBuffer[end] != TAIL_FLAG) {
                end++;
            }

            if (start < mBuffer.length && end >= mBuffer.length) {
                mLog.d("find head, but no tail, copy the left buffer");
                mBuffer = ArrayUtils.subarray(mBuffer, start, mBuffer.length);
                if (DEBUG) {
                    mLog.v("left buffer: " + HexTools.byteArrayToHexReadable(mBuffer));
                }
                return;
            }

            //end points to the last 7E
            if (end != mBuffer.length && (end - start) >= 13) {
                if (DEBUG) {
                    mLog.d("Got a package");
                }

                JT808ProtocolUtils jt808 = new JT808ProtocolUtils();

                try {
                    data = jt808.doEscape4ReceiveBetween(mBuffer, start, end + 1);
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                    data = null;
                }

                byte crc = (byte) BitOperator.getCheckSum4JT808(data, 1, data.length - 1 - 1);
                byte crc2 = data[data.length - 1 - 1];
                if (crc != crc2) {
                    mLog.d("crc:" + crc + " != " + crc2);
                    mLog.e("crc verification failed");
                    start = end + 1;
                    continue;
                }

                if (DEBUG) {
                    mLog.v("got [ Whole ] package: " + HexTools.byteArrayToHexReadable(data));
                }
                start = end + 1;
            } else {
                if (DEBUG) {
                    mLog.d("The context lacks data, loop to next");
                }
                start = end + 1;
                continue;
            }

            LOOP_PACKAGE:
        /* 解析每一包 */
            do {
                final DiagControl diagControl = new DiagControl();
                try {
                    // 解析每包中的每一个部分
                    int j = mStep;
                    for (; j < mItems.size(); ++j, ++mStep) {
                        Item i = mItems.get(j);

                        if (data == null) {
                            mLog.v("no data to parsed, so break LOOP_PACKAGE, current step: " + j + ", got count: " + i.get_recogized_len() + "/" + i.getNeedLen());
                            break LOOP_PACKAGE;
                        }

                        data = i.parse(data);
                        if (i.isParseOk()) {
                            mLog.v("got item: (" + i.getNeedLen() + ")" + HexTools.byteArrayToHex(i.get_recognized()));
                            // 解析成功，继续下一个解析，下一个是数据内容解析，长度可能为0
                            if (j == ITEM_STREAMNUM) {
                                int datalen = 0;

                                if (data.length - 2 > 0) {
                                    datalen = data.length - 2;//
                                }
                                mLog.v("data block len: " + datalen);
                                mItems.get(ITEM_DATA).setNeedLen((int) (datalen));
                            }
                        } else if (i.getStage() == ResultStage.Fail) {
                            throw new ParseException("parse error, step: " + mStep);

                        } else {
                            // cached 状态下直接返回
                            mLog.v("no data to parsed, so break LOOP_PACKAGE");
                            break LOOP_PACKAGE;
                        }

                    }

                    mPackData = makePackData();

                    if (!checkCrc()) {
                        throw new ParseException("parse error, crc error.");
                    }

                    final byte[] byteTemp = mPackData;
                    mLog.v("got [ Good ] package: " + HexTools.byteArrayToHexReadable(mPackData, 50));
                    mLog.v("(mItems.get(ITEM_MSGID).get_recognized(): " + HexTools.byteArrayToHexReadable(mItems.get(ITEM_MSGID).get_recognized(), 50));
                    diagControl.msgID = (short) ByteUtils.makeUShortBig(mItems.get(ITEM_MSGID).get_recognized());
                    diagControl.msgProperty = (short) ByteUtils.makeUShortBig(mItems.get(ITEM_PROP).get_recognized());
                    diagControl.streamNum = (short) ByteUtils.makeUShortBig(mItems.get(ITEM_STREAMNUM).get_recognized());
                    diagControl.phoneNum = mItems.get(ITEM_PHONENUM).get_recognized();
                    diagControl.body = mItems.get(ITEM_DATA).get_recognized();
                    if (DEBUG) {
                        mLog.d("diagControl.msgID:" + diagControl.msgID);
                        mLog.d("diagControl.msgProperty:" + diagControl.msgProperty);
                        mLog.d("diagControl.streamNum:" + diagControl.streamNum);
                    }
                    if (getListener() != null) {
                        // 发送到主线程去执行解析接收包的任务
                        mHandler.post(new Runnable() {
                            public void run() {
                                getListener().onGotPackage(diagControl);
                            }
                        });
                    }

                    if (mSyncListener != null) {
                        mSyncListener.onGotPackage(diagControl);
                    }

                } catch (ParseException e) {
                    e.printStackTrace();
                    mLog.e("ParseException occurred: " + e.getMessage() + ", will reset all cached buffer.");
                    data = null;
                }

                // 重置分段解析状态，并解析下一包。
                resetParseStatus();
            } while (data != null);
        }
    }

    /**
     * 重置解析状态，但Buffer不能清空，此函数不可为 public，仅仅由解析函数调用
     * 仅当解析完一包，或解析出错时调用
     */
    private void resetParseStatus() {
        mStep = 0;
        for (Item i : mItems) {
            i.reset();
        }
    }

    /**
     * 清除所有信息，包括缓存的数据
     * 用于初始化时调用。
     */
    public void resetAll() {
        resetParseStatus();
        mBuffer = null;
    }


    /**
     * 生成字节数据包
     *
     * @return
     */
    private byte[] makePackData() {

        if (mStep != mItems.size()) {
            mLog.w("pack not parsed.");
            return null;
        }

        byte[] result = null;
        int nLen = 0;
        for (int i = 0; i < mItems.size(); ++i) {
            nLen += mItems.get(i).get_recogized_len();
        }

        result = new byte[nLen];
        int k = 0;
        for (int i = 0; i < mItems.size(); ++i) {
            int copyLen = mItems.get(i).get_recogized_len();
            if (copyLen > 0) {
                System.arraycopy(mItems.get(i).get_recognized(), 0, result, k, copyLen);
                k += copyLen;
            }
        }
        return result;
    }


    /**
     * 检验检验字是否正确
     */
    private boolean checkCrc() throws ParseException {
        //crc check
        return true;
    }

    public byte[] getPackData() {
        return mPackData;
    }

    public ParserListener getListener() {
        return mListener;
    }

    /**
     * 设置异步回调，post 到主线程中去执行
     */
    public void setListener(ParserListener listener) {
        this.mListener = listener;
    }

    /**
     * 设置同步回调，相同线程
     */
    public void setSyncListener(ParserListener syncListener) {
        this.mSyncListener = syncListener;
    }

    /**
     * 获取同步回调，相同线程
     */
    public ParserListener getSyncListener() {
        return mSyncListener;
    }


}

