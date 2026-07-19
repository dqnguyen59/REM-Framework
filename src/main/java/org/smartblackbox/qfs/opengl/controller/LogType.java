package org.smartblackbox.qfs.opengl.controller;

public enum LogType {
    none,
    occupancy,
    distanceG,
    ;

    private static String[] strEnums;

    public static String[] getValues() {
        if (strEnums == null) {
            LogType[] v = values();
            strEnums = new String[v.length];
            for (int i = 0; i < v.length; i++) {
                strEnums[i] = v[i].name();
            }
        }
        return strEnums;
    }}
