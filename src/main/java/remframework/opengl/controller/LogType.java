package remframework.opengl.controller;
import remframework.geometry.discovery.*;
import remframework.geometry.discovery.configuration.*;
import remframework.geometry.discovery.realization.session.*;
import remframework.geometry.discovery.viewport.*;
import remframework.geometry.discovery.workflow.*;
import remframework.geometry.discovery.persistence.*;
import remframework.geometry.discovery.ui.*;
import remframework.geometry.discovery.graph.canonical.*;
import remframework.geometry.discovery.graph.symmetry.*;
import remframework.geometry.discovery.optimization.*;

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
