// IMotorControl.aidl
package com.erobbing.smartkey.aidl;

// Declare any non-default types here with import statements

interface IMotorControl {
    /**
     * Demonstrates some basic types that you can use as parameters
     * and return values in AIDL.
     */
    /*void basicTypes(int anInt, long aLong, boolean aBoolean, float aFloat,
            double aDouble, String aString);*/
    boolean getMotorStatus();
    void setMotorStatus(boolean on);
}
