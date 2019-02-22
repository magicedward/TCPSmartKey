package com.erobbing.adb_config_demo.sdk.local_config;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.erobbing.adb_config_demo.AdbConfigDemo;
import com.erobbing.adb_config_demo.sdk.local_config.dealers.Dealer;
import com.erobbing.adb_config_demo.sdk.local_config.dealers.DealerBase;
import com.erobbing.adb_config_demo.sdk.local_config_base.LocalConfigServer;
import com.erobbing.adb_config_demo.sdk.local_config_base.PackMsg;
import com.erobbing.adb_config_demo.sdk.service.SdkService;
import com.erobbing.adb_config_demo.sdk.utils.MyLogger;

import java.util.HashMap;
import java.util.Map;

public class MyLocalConfig extends LocalConfigServer {
    private static final String TAG = "MyLocalConfig";
    private MyLogger mLog = MyLogger.jLog();
    private SdkService mService;

    public MyLocalConfig(SdkService sdkService) {
        // 修改端口号
        mListenPort = 10087;
        mService = sdkService;
    }


    /**
     * 不同命令的执行表
     */
    private Map<Integer, Dealer> cmdMap = new HashMap<Integer, Dealer>() {
        {

            put(AdbConfigDemo.Cmd.cmdReadConfig_VALUE, new DealerBase() {
                @Override
                public void doCmd(long cmd, byte[] data) throws Exception {
                    mLog.i("获取到读配置指令");
                    android.util.Log.e("====", "==========获取到读配置指令");

                    SharedPreferences sp = mService.getSharedPreferences("config", Context.MODE_PRIVATE);

                    AdbConfigDemo.Config.Builder builder = AdbConfigDemo.Config.newBuilder();
                    builder.setErrorCode(AdbConfigDemo.ErrorCode.OK_VALUE);

                    builder.setBoxID(sp.getString("box_id", ""));
                    builder.setShopID(sp.getString("shop_id", ""));
                    builder.setKeyID(sp.getString("key_id", ""));
                    builder.setProvinceID(sp.getString("province_id", ""));
                    builder.setCityID(sp.getString("city_id", ""));
                    builder.setManufacturerID(sp.getString("manufacturer_id", ""));

                    // TODO: 设置其他值

                    // 发送消息给客户端
                    send_resp(cmd, builder.build());
                }
            });

            put(AdbConfigDemo.Cmd.cmdWriteConfig_VALUE, new DealerBase() {
                @Override
                public void doCmd(long cmd, byte[] data) throws Exception {
                    mLog.i("获取到写配置指令");
                    android.util.Log.e("====", "==========获取到写配置指令");

                    // 解析读到的数据
                    AdbConfigDemo.Config config = AdbConfigDemo.Config.parseFrom(data);

                    SharedPreferences sp = mService.getSharedPreferences("config", Context.MODE_PRIVATE);
                    SharedPreferences.Editor ed = sp.edit();
                    ed.putString("shop_id", config.getShopID());
                    ed.putString("box_id", config.getBoxID());
                    ed.putString("key_id", config.getKeyID());
                    ed.putString("province_id", config.getProvinceID());
                    ed.putString("city_id", config.getCityID());
                    ed.putString("manufacturer_id", config.getManufacturerID());
                    ed.putString("ip", config.getIp());
                    ed.putString("ip_mask", config.getIpmask());
                    ed.putString("dns1", config.getDns1());
                    ed.putString("dns2", config.getDns2());
                    ed.putString("gateway", config.getGateway());
                    ed.commit();
                    Log.d(TAG, "SharedPreferences.shop_id=" + config.getShopID() + " - box_id=" + config.getBoxID()
                            + " - key_id=" + config.getKeyID() + " - province_id=" + config.getProvinceID()
                            + " - city_id=" + config.getCityID() + " - manufacturer_id=" + config.getManufacturerID()
                            + " - ip=" + config.getIp() + "- ip_mask=" + config.getIpmask()
                            + " - dns1=" + config.getDns1() + " - dns2=" + config.getDns2()
                            + " - gateway=" + config.getGateway());
                    /*Intent intent = new Intent("com.erobbing.action.PC_TO_DROID_REG");
                    intent.putExtra("shop_id", config.getShopID());
                    intent.putExtra("box_id", config.getBoxID());
                    intent.putExtra("key_id", config.getKeyID());
                    intent.putExtra("province_id", config.getProvinceID());
                    intent.putExtra("city_id", config.getCityID());
                    intent.putExtra("manufacturer_id", config.getManufacturerID());
                    mService.sendBroadcast(intent);*/
                    Intent intent = new Intent("com.erobbing.action.ETHERNET_CHANGE");
                    intent.putExtra("ip", config.getIp());
                    intent.putExtra("ipmask", config.getIpmask());
                    intent.putExtra("gateway", config.getGateway());
                    intent.putExtra("dns1", config.getDns1());
                    intent.putExtra("dns2", config.getDns2());
                    mService.sendBroadcast(intent);
                    // TODO: 保存其他值

                    // 返回消息
                    AdbConfigDemo.Config.Builder builder = AdbConfigDemo.Config.newBuilder();
                    builder.setErrorCode(AdbConfigDemo.ErrorCode.OK_VALUE);
                    send_resp(cmd, builder.build());
                }
            });

            put(AdbConfigDemo.Cmd.cmdRegisterBox_VALUE, new DealerBase() {
                @Override
                public void doCmd(long cmd, byte[] data) throws Exception {
                    mLog.i("注册箱子");
                    android.util.Log.e("====", "==========注册箱子");
                    SharedPreferences sp = mService.getSharedPreferences("config", Context.MODE_PRIVATE);
                    Intent intent = new Intent("com.erobbing.action.PC_TO_DROID_REG");
                    intent.putExtra("shop_id", sp.getString("shop_id", "77777777777777777777"));
                    intent.putExtra("box_id", sp.getString("box_id", "019999999998"));
                    intent.putExtra("key_id", sp.getString("key_id", "007777777778"));
                    intent.putExtra("province_id", sp.getString("province_id", "37"));
                    intent.putExtra("city_id", sp.getString("city_id", "0283"));
                    intent.putExtra("manufacturer_id", sp.getString("manufacturer_id", "88888888"));
                    mService.sendBroadcast(intent);

                    AdbConfigDemo.Config.Builder builder = AdbConfigDemo.Config.newBuilder();
                    builder.setErrorCode(AdbConfigDemo.ErrorCode.OK_VALUE);
                    send_resp(cmd, builder.build());
                }
            });

            put(AdbConfigDemo.Cmd.cmdUnregisterBox_VALUE, new DealerBase() {
                @Override
                public void doCmd(long cmd, byte[] data) throws Exception {
                    mLog.i("注销箱子");
                    android.util.Log.e("====", "==========注销箱子");
                    Intent intent = new Intent("com.erobbing.action.PC_TO_DROID_UNREG");
                    SharedPreferences sp = mService.getSharedPreferences("config", Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = sp.edit();
                    editor.clear();
                    editor.commit();
                    mService.sendBroadcast(intent);

                    AdbConfigDemo.Config.Builder builder = AdbConfigDemo.Config.newBuilder();
                    builder.setErrorCode(AdbConfigDemo.ErrorCode.OK_VALUE);
                    send_resp(cmd, builder.build());
                }
            });

            put(AdbConfigDemo.Cmd.cmdClearAuthCode_VALUE, new DealerBase() {
                @Override
                public void doCmd(long cmd, byte[] data) throws Exception {
                    mLog.i("清除鉴权码");
                    android.util.Log.e("====", "==========清除鉴权码");

                    SharedPreferences sp = mService.getSharedPreferences("config", Context.MODE_PRIVATE);
                    SharedPreferences.Editor ed = sp.edit();
                    ed.putString("auth_code", "unknown");
                    ed.commit();

                    AdbConfigDemo.Config.Builder builder = AdbConfigDemo.Config.newBuilder();
                    builder.setErrorCode(AdbConfigDemo.ErrorCode.OK_VALUE);
                    send_resp(cmd, builder.build());
                }
            });
            // TODO: 在这里增加其他的命令处理方法
        }
    };


    @Override
    public void doWithPack(PackMsg msg) throws Exception {
        Dealer dealer = cmdMap.get((int) msg.cmd);
        if (dealer != null) {
            dealer.doCmd(msg.cmd, msg.data);
        } else {
            mLog.w("不认识的命令字：" + msg.cmd);

            // 原样返回
            AdbConfigDemo.Config.Builder resp = AdbConfigDemo.Config.newBuilder();
            resp.setErrorCode(AdbConfigDemo.ErrorCode.UNKNOWN_CMD_VALUE);
            DealerBase.send_resp(msg.cmd, resp.build());
        }
    }

}
