/*
 * Copyright (C) 2023  Duy Quoc Nguyen <d.q.nguyen@smartblackbox.org> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 01/01/2023
 */
package org.smartblackbox.qfs.opengl.model;

import lombok.Getter;
import lombok.Setter;
import org.ini4j.Wini;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.smartblackbox.qfs.opengl.model.entity.Node;
import org.smartblackbox.utils.AbstractSettings;
import org.smartblackbox.utils.PerformanceMonitor;

@Getter
@Setter
public class DetectorModel extends AbstractSettings {

    private boolean  isIgnited         = false;
    private boolean  isStarted         = false;
    private Vector3i ignitorNode       = new Vector3i(-1, -1, -1);
    private Vector3i detectorNode      = new Vector3i(-1, -1, -1);
    private double   detectionVelocity = 0.00005;
    private int      firstDetectionFrames;
    private int      initFrame;
    private int      firstDetectionFrame;
    private int      secondDetectionFrame;
    private int      secondDetectionFrames;
    private double   nodeVelocity;
    private double   aX                = 0;
    private double   aY                = 0;
    private double   aZ                = 0;
    private double   aLeft             = 0;
    private double   aRight            = 0;
    private double   aTop              = 0;
    private double   aBottom           = 0;
    private double   aFront            = 0;
    private double aBack     = 0;
    private double gradientX = 0;
    private double gradientY = 0;
    private double gradientZ = 0;
    private double pressureX = 0;
    private double pressureY = 0;
    private double pressureZ = 0;
    private double transportVelocityX = 0;
    private double transportVelocityY = 0;
    private double transportVelocityZ = 0;

    public DetectorModel() {
        reset();
    }

    public DetectorModel(DetectorModel model) {
        set(model);
        reset();
    }

    public void set(DetectorModel model) {
        setIgnitorNode(model.ignitorNode);
        setDetectorNode(model.detectorNode);
    }

    @Override
    public AbstractSettings clone() {
        return new DetectorModel(this);
    }

    @Override
    public void reset() {
        isIgnited             = false;
        isStarted             = false;
        initFrame             = -1;
        firstDetectionFrame   = -1;
        secondDetectionFrame  = -1;
        firstDetectionFrames  = -1;
        secondDetectionFrames = -1;
    }

    public void setDetectionVelocity(double detectionVelocity) {
        if (detectionVelocity > 0) this.detectionVelocity = detectionVelocity;
    }

    @Override
    public void loadFromFile(Wini ini, String section, int index) {
        super.loadFromFile(ini, section, index);

        ignitorNode.x     = getInt("Detector", "ignitorNode.x", 1);
        ignitorNode.y     = getInt("Detector", "ignitorNode.y", 1);
        ignitorNode.z     = getInt("Detector", "ignitorNode.z", 1);
        detectorNode.x    = getInt("Detector", "detectorNode.x", 1);
        detectorNode.y    = getInt("Detector", "detectorNode.y", 1);
        detectorNode.z    = getInt("Detector", "detectorNode.z", 1);
        detectionVelocity = getDouble("Detector", "detectVelocity", 0.00005);
    }

    @Override
    public void saveToFile(Wini ini, String section, int index) {
        super.saveToFile(ini, section, index);
        put("Detector", "ignitorNode.x", ignitorNode.x);
        put("Detector", "ignitorNode.y", ignitorNode.y);
        put("Detector", "ignitorNode.z", ignitorNode.z);
        put("Detector", "detectorNode.x", detectorNode.x);
        put("Detector", "detectorNode.y", detectorNode.y);
        put("Detector", "detectorNode.z", detectorNode.z);
        put("Detector", "detectionVelocity", detectionVelocity);
    }

    public void ignite() {
        isIgnited = true;
    }

    public void ignite(Node node, double force) {
        if (node != null) {
            reset();
            isStarted = true;
            node.incPosition(new Vector3d(0, 0, force));
            initFrame = PerformanceMonitor.totalFrames;
        }
    }

    public int detect(Node node) {
        nodeVelocity = node.getVelocity().length();
        if (isStarted) {
            if (node.getVelocity().length() > 0 && firstDetectionFrames == -1) {
                firstDetectionFrame  = PerformanceMonitor.totalFrames;
                firstDetectionFrames = PerformanceMonitor.totalFrames - initFrame;
                return 1;
            }

            if (node.getVelocity().length() > detectionVelocity && isStarted) {
                secondDetectionFrame  = PerformanceMonitor.totalFrames;
                secondDetectionFrames = secondDetectionFrame - initFrame;
                isStarted             = false;
                return 2;
            }
        }

        Vector3d gradient = node.measureGradientImbalance();
        gradientX = gradient.x;
        gradientY = gradient.y;
        gradientZ = gradient.z;

        double velocityGain = 0.1;

        transportVelocityX += gradientX * velocityGain;
        transportVelocityY += gradientY * velocityGain;
        transportVelocityZ += gradientZ * velocityGain;

        pressureX += transportVelocityX;
        pressureY += transportVelocityY;
        pressureZ += transportVelocityZ;

        String s = String.format("GX: %12.8f; GY: %12.8f; GZ: %12.8f === " + //
                                 "VX: %12.8f; VY: %12.8f; VZ: %12.8f === " + //
                                 "PX: %12.8f; PY: %12.8f; PZ: %12.8f; ", //
                                 gradientX, gradientY, gradientZ, //
                                 transportVelocityX, transportVelocityY, transportVelocityZ, //
                                 gradientX, gradientY, gradientZ //
                                 );
        System.out.println(s);

        return 0;
    }

}
