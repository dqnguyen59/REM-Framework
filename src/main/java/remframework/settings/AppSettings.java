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
    private double diamondProbeSourceWidth=8, diamondProbePositionExponent=-12, diamondProbeGradientExponent=-12;
    private double diamondProbeTolerance=1, diamondProbeCycleDeadband=1e-9;
    private boolean diamondProbeRecordTraces, diamondProbeXYOnly=true;
    private double surveyProbeOpacity=.2,surveyHighlightOpacity=1,surveySourceOpacity=1,surveyLineOpacity=1;
    private boolean showLatticeConnections;
    private double latticeConnectionOpacity=.15;
    private int diamondProbeFirstRadius=16,diamondProbeShellSpacing=4,diamondProbeShellCount=2;
    private int orbitFastPeriod=64,orbitRatio=4;
    private int orbitWarmupCycles=1;
    private double orbitWidth=4,orbitAmplitude=1;
    private boolean diamondProbeSphericalSweep;
    private int diamondProbeSweepPeriod = 128;
    private int diamondProbeSweepTurns = 4;
    private int diamondProbeMeasurementCycles = 1;
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
        diamondProbeSourceWidth=validPositive(getDouble("DiamondProbe","sourceWidth",8),8,2);
        diamondProbePositionExponent=validExponent(getDouble("DiamondProbe","positionExponent",-12));
        diamondProbeGradientExponent=validExponent(getDouble("DiamondProbe","gradientExponent",-12));
        diamondProbeTolerance=validPositive(getDouble("DiamondProbe","tolerance",1),1,Double.MIN_VALUE);
        diamondProbeCycleDeadband=validPositive(getDouble("DiamondProbe","cycleDeadband",1e-9),1e-9,Double.MIN_VALUE);
        diamondProbeRecordTraces=getBool("DiamondProbe","recordTraces",false);
        diamondProbeXYOnly=getBool("DiamondProbe","xyOnly",true);
        diamondProbeFirstRadius=Math.max(1,getInt("DiamondProbe","firstRadius",16));
        diamondProbeShellSpacing=Math.max(1,getInt("DiamondProbe","shellSpacing",4));
        diamondProbeShellCount=Math.max(1,getInt("DiamondProbe","shellCount",2));
        orbitWarmupCycles=Math.max(0,Math.min(100,getInt("RotatingOrbit","warmupCycles",1)));
        orbitFastPeriod=Math.max(32,Math.min(4096,getInt("RotatingOrbit","fastPeriod",64)));
        orbitRatio=Math.max(2,Math.min(16,getInt("RotatingOrbit","ratio",4)));
        orbitWidth=getDouble("RotatingOrbit","width",4); if(!Double.isFinite(orbitWidth)||orbitWidth<2)orbitWidth=4;
        orbitAmplitude=getDouble("RotatingOrbit","amplitude",1); if(!Double.isFinite(orbitAmplitude)||orbitAmplitude<=0)orbitAmplitude=1;
        diamondProbeSphericalSweep = getBool("DiamondProbe", "sphericalSweep", false);
        try {
            var sweep = new remframework.geometry.discovery.research.diamond.DiamondSphericalSweep(
                getInt("DiamondProbe", "sweepPeriod", 128), getInt("DiamondProbe", "sweepTurns", 4),
                getInt("DiamondProbe", "measurementCycles", 1));
            diamondProbeSweepPeriod=sweep.updatesPerCycle(); diamondProbeSweepTurns=sweep.turns();
            diamondProbeMeasurementCycles=sweep.measurementCycles();
        } catch (IllegalArgumentException invalid) {
            diamondProbeSweepPeriod=128; diamondProbeSweepTurns=4; diamondProbeMeasurementCycles=1;
        }
        int savedDirections=getInt("DiamondProbe", "xyDirections", 32);
        diamondProbeXYDirections=remframework.opengl.model.DiamondProbePlacement.validXYDirectionCount(savedDirections)?savedDirections:32;
        double savedAmplitude = getDouble("DiamondProbe", "amplitude", 10.0);
        surveyProbeOpacity=validOpacity(getDouble("SurveyDisplay","probeOpacity",.2),.2);
        surveyHighlightOpacity=validOpacity(getDouble("SurveyDisplay","highlightOpacity",1),1);
        surveySourceOpacity=validOpacity(getDouble("SurveyDisplay","sourceOpacity",1),1);
        surveyLineOpacity=validOpacity(getDouble("SurveyDisplay","lineOpacity",1),1);
        showLatticeConnections=getBool("SurveyDisplay","showConnections",false);
        latticeConnectionOpacity=validOpacity(getDouble("SurveyDisplay","connectionOpacity",.15),.15);
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
        put("DiamondProbe","sourceWidth",diamondProbeSourceWidth);
        put("DiamondProbe","positionExponent",diamondProbePositionExponent);
        put("DiamondProbe","gradientExponent",diamondProbeGradientExponent);
        put("DiamondProbe","tolerance",diamondProbeTolerance);
        put("DiamondProbe","cycleDeadband",diamondProbeCycleDeadband);
        put("DiamondProbe","recordTraces",diamondProbeRecordTraces);
        put("DiamondProbe","xyOnly",diamondProbeXYOnly);
        put("DiamondProbe","firstRadius",diamondProbeFirstRadius);
        put("DiamondProbe","shellSpacing",diamondProbeShellSpacing);
        put("DiamondProbe","shellCount",diamondProbeShellCount);
        put("RotatingOrbit","fastPeriod",orbitFastPeriod);put("RotatingOrbit","ratio",orbitRatio);
        put("RotatingOrbit","width",orbitWidth);put("RotatingOrbit","amplitude",orbitAmplitude);
        put("RotatingOrbit","warmupCycles",orbitWarmupCycles);
        put("DiamondProbe", "sphericalSweep", diamondProbeSphericalSweep);
        put("DiamondProbe", "sweepPeriod", diamondProbeSweepPeriod);
        put("DiamondProbe", "sweepTurns", diamondProbeSweepTurns);
        put("DiamondProbe", "measurementCycles", diamondProbeMeasurementCycles);
        put("DiamondProbe", "amplitude", diamondProbeAmplitude);
        put("SurveyDisplay","probeOpacity",surveyProbeOpacity);
        put("SurveyDisplay","highlightOpacity",surveyHighlightOpacity);
        put("SurveyDisplay","sourceOpacity",surveySourceOpacity);
        put("SurveyDisplay","lineOpacity",surveyLineOpacity);
        put("SurveyDisplay","showConnections",showLatticeConnections);
        put("SurveyDisplay","connectionOpacity",latticeConnectionOpacity);
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

    private static double validPositive(double value,double fallback,double minimum) {
        return Double.isFinite(value)&&value>=minimum?value:fallback;
    }

    private static double validOpacity(double value,double fallback){return Double.isFinite(value)?Math.max(0,Math.min(1,value)):fallback;}

    /** Persists the optional background bond display independently of selected-path opacity. */
    public void setLatticeConnectionDisplay(boolean show,double opacity) {
        opacity=validOpacity(opacity,.15);
        if(show==showLatticeConnections&&opacity==latticeConnectionOpacity)return;
        showLatticeConnections=show;latticeConnectionOpacity=opacity;
        if(!getCurrentFilename().isEmpty())saveToFile();
    }

    /** Persists renderer-only opacity; zero hides a category and one makes it opaque. */
    public void setSurveyDisplayOpacity(double probes,double highlights,double source,double lines) {
        probes=validOpacity(probes,.2);highlights=validOpacity(highlights,1);source=validOpacity(source,1);lines=validOpacity(lines,1);
        if(probes==surveyProbeOpacity&&highlights==surveyHighlightOpacity&&source==surveySourceOpacity&&lines==surveyLineOpacity)return;
        surveyProbeOpacity=probes;surveyHighlightOpacity=highlights;surveySourceOpacity=source;surveyLineOpacity=lines;
        if(!getCurrentFilename().isEmpty())saveToFile();
    }
    private static double validExponent(double value) {
        return Double.isFinite(value)&&value>=-18&&value<=0&&value==Math.rint(value)?value:-12;
    }
    public void setDiamondProbeDetection(double width,double positionExponent,double gradientExponent,double tolerance) {
        if(validPositive(width,-1,2)!=width || validExponent(positionExponent)!=positionExponent
            ||validExponent(gradientExponent)!=gradientExponent||validPositive(tolerance,-1,Double.MIN_VALUE)!=tolerance)
            throw new IllegalArgumentException("Invalid source width, threshold exponents or tolerance");
        if(width!=diamondProbeSourceWidth||positionExponent!=diamondProbePositionExponent
            ||gradientExponent!=diamondProbeGradientExponent||tolerance!=diamondProbeTolerance) {
            diamondProbeSourceWidth=width;diamondProbePositionExponent=positionExponent;
            diamondProbeGradientExponent=gradientExponent;diamondProbeTolerance=tolerance;
            if(!getCurrentFilename().isEmpty())saveToFile();
        }
    }
    public void setDiamondProbeShellLayout(int first,int spacing,int count,boolean xy) {
        if(first<1||spacing<1||count<1)throw new IllegalArgumentException("Positive shell dimensions required");
        if(first!=diamondProbeFirstRadius||spacing!=diamondProbeShellSpacing||count!=diamondProbeShellCount||xy!=diamondProbeXYOnly) {
            diamondProbeFirstRadius=first;diamondProbeShellSpacing=spacing;diamondProbeShellCount=count;diamondProbeXYOnly=xy;
            if(!getCurrentFilename().isEmpty())saveToFile();
        }
    }
    public void setDiamondProbeDiagnostics(double deadband,boolean traces) {
        if(!Double.isFinite(deadband)||deadband<=0)throw new IllegalArgumentException("Positive finite deadband required");
        if(deadband!=diamondProbeCycleDeadband||traces!=diamondProbeRecordTraces) {
            diamondProbeCycleDeadband=deadband;diamondProbeRecordTraces=traces;
            if(!getCurrentFilename().isEmpty())saveToFile();
        }
    }
    public void setOrbitWarmupCycles(int cycles) {
        if(cycles<0||cycles>100)throw new IllegalArgumentException("Warm-up cycles 0..100");
        if(cycles!=orbitWarmupCycles){orbitWarmupCycles=cycles;if(!getCurrentFilename().isEmpty())saveToFile();}
    }
    public void setOrbitConfiguration(int period,int ratio,double width,double amplitude) {
        new remframework.geometry.discovery.research.diamond.DiamondRotatingOrbit(period,ratio);
        if(!Double.isFinite(width)||width<2||!Double.isFinite(amplitude)||amplitude<=0)throw new IllegalArgumentException("Invalid orbit source");
        if(period!=orbitFastPeriod||ratio!=orbitRatio||width!=orbitWidth||amplitude!=orbitAmplitude) {
            orbitFastPeriod=period;orbitRatio=ratio;orbitWidth=width;orbitAmplitude=amplitude;
            if(!getCurrentFilename().isEmpty())saveToFile();
        }
    }
    public void setDiamondProbeSphericalSweep(boolean enabled) {
        if(diamondProbeSphericalSweep!=enabled) {
            diamondProbeSphericalSweep=enabled;
            if(!getCurrentFilename().isEmpty())saveToFile();
        }
    }
    public void setDiamondProbeSweepConfiguration(int period,int turns,int cycles) {
        new remframework.geometry.discovery.research.diamond.DiamondSphericalSweep(period,turns,cycles);
        if(period!=diamondProbeSweepPeriod || turns!=diamondProbeSweepTurns || cycles!=diamondProbeMeasurementCycles) {
            diamondProbeSweepPeriod=period; diamondProbeSweepTurns=turns; diamondProbeMeasurementCycles=cycles;
            if(!getCurrentFilename().isEmpty())saveToFile();
        }
    }
    // Keep individual setters validated as well as the atomic GUI configuration setter.
    public void setDiamondProbeSweepPeriod(int period) {setDiamondProbeSweepConfiguration(period,diamondProbeSweepTurns,diamondProbeMeasurementCycles);}
    public void setDiamondProbeSweepTurns(int turns) {setDiamondProbeSweepConfiguration(diamondProbeSweepPeriod,turns,diamondProbeMeasurementCycles);}
    public void setDiamondProbeMeasurementCycles(int cycles) {setDiamondProbeSweepConfiguration(diamondProbeSweepPeriod,diamondProbeSweepTurns,cycles);}

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
