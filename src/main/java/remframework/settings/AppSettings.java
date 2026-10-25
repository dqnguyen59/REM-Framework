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
package remframework.settings;

import lombok.Getter;
import lombok.Setter;
import org.ini4j.Profile.Section;
import org.ini4j.Wini;
import org.lwjgl.nuklear.NkRect;
import remframework.helpers.Constants;
import remframework.helpers.AbstractSettings;
import remframework.helpers.ISettings;
import remframework.helpers.Utils;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
public class AppSettings extends AbstractSettings implements ISettings {

    private static AppSettings instance;

    public static int MAX_NUM_THREADS = Runtime.getRuntime().availableProcessors();
    private       int numThreads      = MAX_NUM_THREADS;

    private boolean useFixedNodes = true;
    // Range: 0.0 - 1.0
    private double  absorption    = 1.0;
    private boolean useLoop;
    private boolean isChanged;
    private String latticeModelIdentifier = "system:cubic";
    private int diamondProbeMaximumUpdates = 200;
    private double diamondProbeAmplitude = 10.0;
    private int diamondProbeXYDirections = 32;
    private remframework.geometry.discovery.research.diamond.DiamondExcitationAxis diamondProbeExcitationAxis = remframework.geometry.discovery.research.diamond.DiamondExcitationAxis.Z;

    private String projectFilePath = Constants.PROJECT_FILE_PATH;

    private String fontFile       = Constants.DEFAULT_FONT_FILE;
    private String fontFileBold   = Constants.DEFAULT_FONT_FILE_BOLD;
    private String fontFileItalic = Constants.DEFAULT_FONT_FILE_ITALIC;
    private int    fontSize       = Constants.DEFAULT_FONT_SIZE;

    private int windowLeft   = -9999;
    private int windowTop    = -9999;
    private int windowWidth  = 860;
    private int windowHeight = 640;
    private int displayWidth;
    private int displayHeight;
    private int maximized    = 0;

    private int defaultDimensionX = Constants.DEFAULT_DIMENSION_X;
    private int defaultDimensionY = Constants.DEFAULT_DIMENSION_Y;
    private int defaultDimensionZ = Constants.DEFAULT_DIMENSION_Z;

    private HashMap<String, NkRect> frameRectMap = new HashMap<>();

    private char          decimalSeperator   = '.';
    private DecimalFormat formatInt          = Utils.setFormat(0, 0, decimalSeperator, false, false);
    private DecimalFormat formatDefault      = Utils.setFormat(2, 2, decimalSeperator, false, false);
    private DecimalFormat formatScientific2  = Utils.setFormat(2, 2, decimalSeperator, false, false);
    private DecimalFormat formatScientific4  = Utils.setFormat(4, 4, decimalSeperator, false, false);
    private DecimalFormat formatScientific8  = Utils.setFormat(8, 8, decimalSeperator, false, false);
    private DecimalFormat formatScientific9  = Utils.setFormat(9, 9, decimalSeperator, false, false);
    private DecimalFormat formatScientific12 = Utils.setFormat(12, 12, decimalSeperator, false, false);

    private int antiAliasing = 1;

    public static AppSettings getInstance() {
        if (instance == null) {
            instance = new AppSettings();
        }
        return instance;
    }

    public AppSettings() {
        super();
    }

    public AppSettings(AppSettings settings) {
        super();
        set(settings);
    }

    public void set(AppSettings settings) {
        setWindowLeft(settings.windowLeft);
        setWindowTop(settings.windowTop);
        setWindowWidth(settings.windowWidth);
        setWindowHeight(settings.windowHeight);
        setMaximized(settings.maximized);
    }

    @Override
    public AbstractSettings clone() {
        return new AppSettings(this);
    }

    @Override
    public void reset() {
        // Do nothing
    }

    @Override
    public void loadFromFile(Wini ini, String section, int index) {
        super.loadFromFile(ini, section, index);

        numThreads    = getInt("System", "numThreads", MAX_NUM_THREADS);
        useFixedNodes = getBool("System", "useFixedNodes", true);
        useLoop       = getBool("System", "useLoop", false);
        latticeModelIdentifier = getString("System", "latticeModel", "system:cubic");
        diamondProbeMaximumUpdates = Math.max(1, getInt("DiamondProbe", "maximumUpdates", 200));
        int savedDirections=getInt("DiamondProbe", "xyDirections", 32);
        diamondProbeXYDirections=remframework.opengl.model.DiamondProbePlacement.validXYDirectionCount(savedDirections)?savedDirections:32;
        double savedAmplitude = getDouble("DiamondProbe", "amplitude", 10.0);
        diamondProbeAmplitude = Double.isFinite(savedAmplitude) && savedAmplitude > 0 ? savedAmplitude : 10.0;
        try { diamondProbeExcitationAxis=remframework.geometry.discovery.research.diamond.DiamondExcitationAxis.valueOf(getString("DiamondProbe","excitationAxis","Z")); }
        catch(IllegalArgumentException error){diamondProbeExcitationAxis=remframework.geometry.discovery.research.diamond.DiamondExcitationAxis.Z;}
        if(diamondProbeExcitationAxis==remframework.geometry.discovery.research.diamond.DiamondExcitationAxis.AUTO)
            diamondProbeExcitationAxis=remframework.geometry.discovery.research.diamond.DiamondExcitationAxis.Z;
        numThreads = Math.max(1, Math.min(numThreads, MAX_NUM_THREADS));

        setProjectFilePath(getString("System", "projectFilePath", Constants.PROJECT_FILE_PATH));
        setFontFile(getString("System", "fontFile", Constants.DEFAULT_FONT_FILE));
        setFontFileBold(getString("System", "fontFileBold", Constants.DEFAULT_FONT_FILE_BOLD));
        setFontFileItalic(getString("System", "fontFileItalic", Constants.DEFAULT_FONT_FILE_ITALIC));
        setFontSize(getInt("System", "fontSize", Constants.DEFAULT_FONT_SIZE));
        setWindowLeft(getInt("Window", "left", -9999));
        setWindowTop(getInt("Window", "top", -9999));
        setWindowWidth(getInt("Window", "width", 860));
        setWindowHeight(getInt("Window", "height", 640));
        setMaximized(getInt("Window", "maximized", 0));
        setAntiAliasing(getInt("OpenGL", "antiAliasing", 1));
        setDefaultDimensionX(getInt("DefaultDimension", "x", Constants.DEFAULT_DIMENSION_X));
        setDefaultDimensionY(getInt("DefaultDimension", "y", Constants.DEFAULT_DIMENSION_Y));
        setDefaultDimensionZ(getInt("DefaultDimension", "z", Constants.DEFAULT_DIMENSION_Z));

        String  sFrame       = "Frame";
        Section frameSection = ini.get(sFrame);
        if (frameSection != null) {
            for (String key : frameSection.keySet()) {
                String value = getString(sFrame, key);
                if (!value.isEmpty()) {
                    String[] array = value.split(",");
                    if (array.length != 4) {
                        System.err.println("Ignoring malformed frame bounds for '" + key
                                + "': " + value);
                        continue;
                    }

                    NkRect rect = NkRect.create();
                    try {
                        rect.x(Float.parseFloat(array[0].trim()));
                        rect.y(Float.parseFloat(array[1].trim()));
                        rect.w(Float.parseFloat(array[2].trim()));
                        rect.h(Float.parseFloat(array[3].trim()));
                        frameRectMap.put(key, rect);
                    } catch (NumberFormatException exception) {
                        System.err.println("Ignoring malformed frame bounds for '" + key
                                + "': " + value);
                    }
                }
            }
        }
    }

    @Override
    public void saveToFile(Wini ini, String section, int index) {
        super.saveToFile(ini, section, index);
        put("System", "numThreads", numThreads);
        put("System", "useFixedNodes", useFixedNodes);
        put("System", "useLoop", useLoop);
        put("System", "latticeModel", latticeModelIdentifier);
        put("DiamondProbe", "maximumUpdates", diamondProbeMaximumUpdates);
        put("DiamondProbe", "amplitude", diamondProbeAmplitude);
        put("DiamondProbe", "xyDirections", diamondProbeXYDirections);
        put("DiamondProbe", "excitationAxis", diamondProbeExcitationAxis.name());
        put("System", "projectFilePath", projectFilePath);
        put("System", "fontFile", fontFile);
        put("System", "fontFileBold", fontFileBold);
        put("System", "fontFileItalic", fontFileItalic);
        put("System", "fontSize", fontSize);
        put("Window", "left", windowLeft);
        put("Window", "top", windowTop);
        put("Window", "width", windowWidth);
        put("Window", "height", windowHeight);
        put("Window", "maximized", maximized);
        put("OpenGL", "antiAliasing", antiAliasing);
        put("DefaultDimension", "x", defaultDimensionX);
        put("DefaultDimension", "y", defaultDimensionY);
        put("DefaultDimension", "z", defaultDimensionZ);

        String sFrame = "Frame";
        for (Map.Entry<String, NkRect> entry : frameRectMap.entrySet()) {
            String key   = entry.getKey();
            NkRect value = entry.getValue();
            // Colons have INI meaning and can be present in dialog-generated frame names.
            // Persist a safe key so frame layout cannot corrupt the settings file.
            put(sFrame, key.replace(':', '_'),
                    value.x() + "," + value.y() + "," + value.w() + "," + value.h());
        }
    }

    /** Returns saved startup dimensions, with a safe fallback for legacy diamond settings. */
    public org.joml.Vector3i startupDimensions(boolean diamond) {
        var dimensions = new org.joml.Vector3i(defaultDimensionX, defaultDimensionY, defaultDimensionZ);
        if (diamond && !remframework.lattice.ExperimentalDiamondLatticeModel.validDimensions(dimensions))
            return new org.joml.Vector3i(80, 80, 64);
        return dimensions;
    }

    /** Remembers a successfully loaded size for the next argument-free launch. */
    public void rememberDimensions(org.joml.Vector3i dimensions) {
        defaultDimensionX = dimensions.x;
        defaultDimensionY = dimensions.y;
        defaultDimensionZ = dimensions.z;
        if (!getCurrentFilename().isEmpty()) saveToFile();
    }

    public void setDiamondProbeMaximumUpdates(int updates) {
        if (updates < 1) throw new IllegalArgumentException("Maximum updates must be positive");
        if (diamondProbeMaximumUpdates != updates) {
            diamondProbeMaximumUpdates = updates;
            if (!getCurrentFilename().isEmpty()) saveToFile();
        }
    }

    public void setDiamondProbeAmplitude(double amplitude) {
        if (!Double.isFinite(amplitude) || amplitude <= 0)
            throw new IllegalArgumentException("Amplitude must be positive and finite");
        if (diamondProbeAmplitude != amplitude) {
            diamondProbeAmplitude = amplitude;
            if (!getCurrentFilename().isEmpty()) saveToFile();
        }
    }
    public void setDiamondProbeXYDirections(int count) {
        if(!remframework.opengl.model.DiamondProbePlacement.validXYDirectionCount(count))
            throw new IllegalArgumentException("Choose 8, 16, 32 or 64 XY directions");
        if(diamondProbeXYDirections!=count) {
            diamondProbeXYDirections=count;
            if(!getCurrentFilename().isEmpty())saveToFile();
        }
    }
    public void setDiamondProbeExcitationAxis(remframework.geometry.discovery.research.diamond.DiamondExcitationAxis axis) {
        java.util.Objects.requireNonNull(axis);
        if(axis==remframework.geometry.discovery.research.diamond.DiamondExcitationAxis.AUTO)
            throw new IllegalArgumentException("Choose an explicit X, Y or Z excitation axis");
        if(diamondProbeExcitationAxis!=axis) {
            diamondProbeExcitationAxis=axis;
            if(!getCurrentFilename().isEmpty())saveToFile();
        }
    }

    public void setNumThreads(int numThreads) {
        int normalizedThreads = Math.max(1, Math.min(numThreads, MAX_NUM_THREADS));
        if (this.numThreads != normalizedThreads) {
            this.numThreads = normalizedThreads;
            saveToFile();
        }
    }

    public void setUseFixedNodes(boolean useFixedNodes) {
        if (this.useFixedNodes != useFixedNodes) {
            this.useFixedNodes = useFixedNodes;
            saveToFile();
            isChanged = true;
        }
    }

    public void setUseLoop(boolean useLoop) {
        if (this.useLoop != useLoop) {
            this.useLoop = useLoop;
            saveToFile();
            isChanged = true;
        }
    }

    public void setLatticeModelIdentifier(String identifier) {
        if (!latticeModelIdentifier.equals(identifier)) {
            latticeModelIdentifier = identifier;
            if (!getCurrentFilename().isEmpty()) saveToFile();
            isChanged = true;
        }
    }

    public void setLatticeModelIdentifierSilently(String identifier) {
        if (!latticeModelIdentifier.equals(identifier)) {
            latticeModelIdentifier = identifier;
            if (!getCurrentFilename().isEmpty()) saveToFile();
        }
    }

    public void requestLatticeRebuild() {
        isChanged = true;
    }

    /**
     * Use this function to check if reset is required.
     * If true, then define your customized reset.
     *
     */
    public boolean isChanged() {
        if (isChanged) {
            isChanged = false;
            return true;
        }
        return false;
    }


    public void setWindowWidth(int windowWidth) {
        if (this.windowWidth != windowWidth) {
            this.windowWidth = windowWidth;
            Project.getInstance().camera.updateProjectionMatrix();
        }
    }

    public void setWindowHeight(int windowHeight) {
        if (this.windowHeight != windowHeight) {
            this.windowHeight = windowHeight;
            Project.getInstance().camera.updateProjectionMatrix();
        }
    }

}
