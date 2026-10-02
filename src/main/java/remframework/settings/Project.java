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
import org.ini4j.Wini;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.lwjgl.glfw.GLFW;
import remframework.opengl.model.*;
import remframework.helpers.Constants;
import remframework.measurement.gravity.GravityExperimentController;
import remframework.measurement.gravitypressure.GravityPressureExperimentController;
import remframework.measurement.detector.Detector;
import remframework.measurement.detector.DetectorConfiguration;
import remframework.measurement.detector.DetectorExperimentController;
import remframework.measurement.detector.FeasibilityPilotTelemetry;
import remframework.opengl.controller.TransportSystem;
import remframework.opengl.model.entity.Entity;
import remframework.opengl.model.entity.Node;
import remframework.opengl.model.entity.Occupancy;
import remframework.opengl.model.lights.Light;
import remframework.opengl.helpers.OBJFormatLoader;
import remframework.helpers.AbstractSettings;
import remframework.helpers.ISettings;
import remframework.helpers.Utils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class Project extends AbstractSettings implements ISettings {

    private static Project instance;

    private boolean isChanged;
    private boolean isFileSaved;
    private boolean headlessLoading;

    /**
     * Set the universe dimension.
     * 0: number of x nodes
     * 1: number of y nodes
     * 2: number of z nodes
     */
    private Vector3i dimension = new Vector3i();

    private double constantFrequency      = 1.00;
    private double constantWaveSpeed      = 1.00;
    private double constantElectricFactor = 0.12;
    private double constantMagneticFactor = 0.94;

    // Don't set the glSwapInterval below 1! It will cause instability of the application and freeze.
    private int glSwapInterval = 1;

    private RemModel remModel = new RemModel();
    public  Camera   camera   = new Camera();
    public  Scene    scene;
    public  Terrain  terrain;
    public  Vector3d baseRotation = new Vector3d(-90, 0, 0);

    public Settings         settings      = new Settings();
    public SlitWallSettings slitWall      = new SlitWallSettings();
    public DetectorModel detectorModel = new DetectorModel();
    public RainModel     rainModel     = new RainModel();

    private final ArrayList<Light> lights = new ArrayList<>();

    public List<Occupancy> occupancies = new ArrayList<>();
    private List<ProbeDefinition> probeDefinitions = new ArrayList<>();
    private List<Detector> detectors = List.of();

    public static Project getInstance() {
        if (instance == null) {
            instance       = new Project();
            instance.scene = new Scene();
        }
        return instance;
    }

    public Project() {
        super();
    }

    /** Atomically replaces session-generated probes while preserving non-generated receivers. */
    public void replaceGeneratedProbeDefinitions(List<ProbeDefinition> generatedDefinitions) {
        List<ProbeDefinition> replacement = new ArrayList<>();
        for (ProbeDefinition definition : probeDefinitions) {
            if (!definition.isGenerated()) replacement.add(definition);
        }
        replacement.addAll(generatedDefinitions);
        probeDefinitions = replacement;
    }

    public Project(Project project) {
        super();
        set(project);
    }

    public void set(Project project) {
        isChanged              = project.isChanged;
        isFileSaved            = project.isFileSaved;
        dimension.x            = project.getDimensionX();
        dimension.y            = project.getDimensionY();
        dimension.z            = project.getDimensionZ();
        constantFrequency      = project.constantFrequency;
        constantWaveSpeed      = project.constantWaveSpeed;
        constantElectricFactor = project.constantElectricFactor;
        constantMagneticFactor = project.constantMagneticFactor;
        glSwapInterval         = project.glSwapInterval;

        camera.setFieldOfView(project.camera.getFieldOfView());
        camera.setZ_near(project.camera.getZ_near());
        camera.setZ_far(project.camera.getZ_far());
        Vector3f pos = project.camera.getPosition();
        camera.setPosition(pos.x, pos.y, pos.z);
        Vector3f rot = project.camera.getRotation();
        camera.setRotation(rot.x, rot.y, rot.z);
        baseRotation.x = project.baseRotation.x;
        baseRotation.y = project.baseRotation.y;
        baseRotation.z = project.baseRotation.z;
    }

    @Override
    public Project clone() {
        return new Project(this);
    }

    public void loadHeadlessFromFile(String filename) {
        headlessLoading = true;
        try {
            super.loadFromFile(filename);
        } finally {
            headlessLoading = false;
        }
    }

    @Override
    public void loadFromFile(Wini ini, String section, int index) {
        System.out.println("Project.loadFromFile() started");
        super.loadFromFile(ini, section, index);

        setConstantFrequency(getDouble("QuantumField", "constantFrequency", 1.0));
        setConstantElectricFactor(getDouble("QuantumField", "constantElectricFactor", 0.025));
        setConstantMagneticFactor(getDouble("QuantumField", "constantMagneticFactor", 0.0003));
        setGlSwapInterval(getInt("QuantumField", "glSwapInterval", 1));
        scene.getLattice().setNumEdgeAbsorptionLayers(
                getInt("QuantumField", "numEdgeAbsorptionLayers", 30));
        int x = getInt("QuantumField", "dimension.x", 15);
        int y = getInt("QuantumField", "dimension.y", 15);
        int z = getInt("QuantumField", "dimension.z", 15);
        setDimension(x, y, z, false);

        camera.setFieldOfView(getFloat("Camera", "fieldOfFiew", 45));
        camera.getPosition().x = getFloat("Camera", "position.x", 0);
        camera.getPosition().y = getFloat("Camera", "position.y", 0);
        camera.getPosition().z = getFloat("Camera", "position.z", 120);
        camera.getRotation().x = getFloat("Camera", "rotation.x", 0);
        camera.getRotation().y = getFloat("Camera", "rotation.y", 0);
        camera.getRotation().z = getFloat("Camera", "rotation.z", 1);
        baseRotation.x         = getDouble("Base", "rotation.x", 0.0);
        baseRotation.y         = getDouble("Base", "rotation.y", 0.0);
        baseRotation.z         = getDouble("Base", "rotation.z", 0.0);

        if (terrain != null) {
            terrain.loadFromFile(ini, "Terrain", index);
        }

        if (!headlessLoading) {
            loadLights(ini);
        }

        settings.loadFromFile(ini, "QFSSettings", 0);
        slitWall.loadFromFile(ini, "SlitWall", 0);

        probeDefinitions.clear();

        int size = getInt("Oscillators", "Size", 0);

        TransportSystem.getInstance().loadFromFile(ini, "TransportSystem", 0);

        occupancies.clear();
        for (int i = 0; i < size; i++) {
            Occupancy occupancy = new Occupancy();
            occupancy.loadFromFile(ini, "Oscillator", i);
            occupancies.add(occupancy);
        }

        if (Path.of(currentFilename).getFileName().toString().equals("detectGradient8.qfs")) {
            TransportSystem.getInstance().configureReceiverStatusLogging(
                    Path.of("measurements", "detectGradient8-receiver-status.csv"));
        } else {
            TransportSystem.getInstance().closeReceiverStatusLogging();
        }

        detectors = DetectorConfiguration.load(ini, dimension);
        DetectorExperimentController detectorController =
                DetectorExperimentController.getInstance();
        detectorController.disable();

        String projectBaseName = Path.of(currentFilename).getFileName().toString();
        if (!detectors.isEmpty()) {
            GravityExperimentController.getInstance().setEnabled(false);
            GravityPressureExperimentController.getInstance().setEnabled(false);
            String experimentName = projectBaseName.substring(
                    0, projectBaseName.length() - ".qfs".length());
            boolean feasibilityPilot =
                    getBool("FeasibilityPilot", "enabled", false);
            Vector3i sourceIndex = occupancies.isEmpty()
                                   ? null : occupancies.getFirst().getNodeIndex();
            Path experimentDirectory = Path.of("measurements", experimentName);
            Path outputFile = feasibilityPilot
                              ? experimentDirectory.resolve(
                                      "raw-detectors-run-001.csv")
                              : Path.of("measurements",
                                      experimentName + "-run-001.csv");
            try {
                detectorController.configure(outputFile, detectors, sourceIndex);
                if (feasibilityPilot) {
                    int configuredUpdates = getInt(
                            "FeasibilityPilot", "updates", 180);
                    FeasibilityPilotTelemetry telemetry =
                            FeasibilityPilotTelemetry.getInstance();
                    telemetry.configure(
                            experimentDirectory.resolve(
                                    "runtime-memory-telemetry.csv"),
                            experimentName, dimension.x, configuredUpdates);
                    telemetry.markDetectorsInitialized();
                }
            } catch (Exception exception) {
                throw new IllegalStateException(
                        "Could not configure passive detectors: "
                                + exception.getMessage(), exception);
            }
            System.out.println("Passive detector instrumentation enabled");
            System.out.println("Detectors: " + detectors.size());
            System.out.println("CSV: " + outputFile);
        }
        else if (projectBaseName.startsWith("gravity-test-2") && occupancies.size() >= 2) {
            GravityExperimentController.getInstance().setEnabled(false);
            String experimentName = projectBaseName.substring(
                    0, projectBaseName.length() - ".qfs".length());
            boolean sourceEnabled = !experimentName.contains("source-off");
            String receiverConfiguration = experimentName.contains("zero-receiver")
                                           ? "zero-amplitude receiver gate"
                                           : "active Z sin amplitude 0.01";
            GravityPressureExperimentController controller =
                    GravityPressureExperimentController.getInstance();
            try {
                Path outputFile = controller.configure(
                        experimentName, sourceEnabled, receiverConfiguration,
                        occupancies.get(0),
                        new ArrayList<>(occupancies.subList(1, occupancies.size())));
                controller.setEnabled(true);
                System.out.println("Gravity Test 2 enabled");
                System.out.println("Source: " + occupancies.get(0).getIdentity().getName());
                System.out.println("Receivers: " + (occupancies.size() - 1));
                System.out.println("CSV: " + outputFile);
            } catch (Exception e) {
                controller.setEnabled(false);
                System.err.println("Gravity Test 2 logging disabled: " + e.getMessage());
            }
        }
        // Temporary Gravity Test 1 configuration. Remove once experiment selection becomes configurable.
        else if (occupancies.size() >= 2) {
            GravityExperimentController controller = GravityExperimentController.getInstance();
            int runNumber = 1;
            Path outputFile;
            do {
                outputFile = Path.of(
                        "measurements",
                        String.format("gravity-test-1-run-%03d.csv", runNumber++));
            } while (Files.exists(outputFile));

            controller.configure(outputFile);
            controller.designateSourceIdentity(occupancies.get(0).getIdentity());
            controller.clearMeasurementIdentities();
            for (int i = 1; i < occupancies.size(); i++) {
                controller.addMeasurementIdentity(occupancies.get(i).getIdentity());
            }
            controller.setEnabled(true);

            System.out.println("Gravity Test 1 enabled");
            System.out.println("Source: " + occupancies.get(0).getIdentity().getName());
            System.out.println("Measurement points: " + (occupancies.size() - 1));
            System.out.println("CSV: " + outputFile);
        }

        rainModel.loadFromFile(ini, "Rain", index);
        detectorModel.loadFromFile(ini, "Detector", index);

        if (!occupancies.isEmpty())
            remModel.setSelectedOccupancy(occupancies.getFirst());

        if (settings.getVisibleIndexX() >= dimension.x) {
            settings.setVisibleIndexX(dimension.x / 2);
        }

        if (settings.getVisibleIndexY() >= dimension.y) {
            settings.setVisibleIndexY(dimension.y / 2);
        }

        if (settings.getVisibleIndexZ() >= dimension.z) {
            settings.setVisibleIndexZ(dimension.z / 2);
        }

        remModel.getCurrentMouseNodeIndex().x = dimension.x / 2;
        remModel.getCurrentMouseNodeIndex().y = dimension.y / 2;
        remModel.getCurrentMouseNodeIndex().z = dimension.z / 2;

        reset();
        remModel.setLoadingReady(false);
        if (!headlessLoading) {
            Thread.startVirtualThread(() -> {
                while (!remModel.isLoadingReady()) {
                    Utils.sleepMS(100);
                }
                Utils.sleepMS(200);
                loadWalls(ini);
                System.out.println("Project loadFromFile() ready");
            });
        }
    }

    @Override
    public void saveToFile(Wini ini, String section, int index) {
        super.saveToFile(ini, section, index);
        put("QuantumField", "constantFrequency", constantFrequency);
        put("QuantumField", "constantElectricFactor", constantElectricFactor);
        put("QuantumField", "constantMagneticFactor", constantMagneticFactor);
        put("QuantumField", "glSwapInterval", glSwapInterval);
        put("QuantumField", "numEdgeAbsorptionLayers",
                scene.getLattice().getNumEdgeAbsorptionLayers());
        put("QuantumField", "dimension.x", dimension.x);
        put("QuantumField", "dimension.y", dimension.y);
        put("QuantumField", "dimension.z", dimension.z);

        saveLights(ini);

        baseRotation = scene.getLattice().getBaseEntity().getRotation();

        put("Camera", "fieldOfFiew", camera.getFieldOfView());
        put("Camera", "position.x", camera.getPosition().x);
        put("Camera", "position.y", camera.getPosition().y);
        put("Camera", "position.z", camera.getPosition().z);
        put("Camera", "rotation.x", camera.getRotation().x);
        put("Camera", "rotation.y", camera.getRotation().y);
        put("Camera", "rotation.z", camera.getRotation().z);
        put("Base", "rotation.x", baseRotation.x);
        put("Base", "rotation.y", baseRotation.y);
        put("Base", "rotation.z", baseRotation.z);

        terrain.saveToFile(ini, "Terrain", index);

        settings.saveToFile(ini, "QFSSettings", 0);
        slitWall.saveToFile(ini, "SlitWall", 0);

        String value   = get("Oscillators", "Size");
        int    oldSize = value == null || value.isEmpty() ? 0 : Integer.parseInt(value);
        for (int i = 0; i < oldSize; i++) {
            ini.remove("Oscillator" + i);
        }

        TransportSystem.getInstance().saveToFile(ini, "TransportSystem", 0);

        int size = occupancies.size();
        put("Oscillators", "Size", size);
        for (int i = 0; i < size; i++) {
            occupancies.get(i).saveToFile(ini, "Oscillator", i);
        }

        put("Detectors", "Size", detectors.size());
        for (int i = 0; i < detectors.size(); i++) {
            Detector detector = detectors.get(i);
            Vector3i nodeIndex = detector.getNodeIndex();
            put("Detector" + i, "name", detector.getName());
            put("Detector" + i, "nodeIndex.x", nodeIndex.x);
            put("Detector" + i, "nodeIndex.y", nodeIndex.y);
            put("Detector" + i, "nodeIndex.z", nodeIndex.z);
            if (detector.getRequestedRadius() != null) {
                put("Detector" + i, "requestedRadius",
                        detector.getRequestedRadius());
            }
            if (detector.getRequestedAngleDegrees() != null) {
                put("Detector" + i, "requestedAngleDegrees",
                        detector.getRequestedAngleDegrees());
            }
            if (detector.getDirection() != null) {
                put("Detector" + i, "direction", detector.getDirection());
            }
            if (detector.getDescription() != null) {
                put("Detector" + i, "description",
                        detector.getDescription());
            }
        }

        rainModel.saveToFile(ini, "Rain", index);
        detectorModel.saveToFile(ini, "Detector", index);

        saveWalls(ini);
        isFileSaved = true;
    }

    private void loadWalls(Wini ini) {
        System.out.println("Project.loadWalls()");
        Vector3i nodeIndex;

        scene.clearAllWalls();
        scene.getWallNodeIndexList().clear();

        int numWalls = getInt("Walls", "numWalls", 0);
        for (int i = 0; i < numWalls; i++) {
            nodeIndex   = new Vector3i();
            nodeIndex.x = getInt("Wall" + i, "index.x", -1);
            nodeIndex.y = getInt("Wall" + i, "index.y", -1);
            nodeIndex.z = getInt("Wall" + i, "index.z", -1);
            Node node = scene.getLattice().getNodeByIndex(nodeIndex);
            if (node != null) {
                scene.getWallNodeIndexList().add(nodeIndex);
            }
        }
        scene.updateWalls();
    }

    private void saveWalls(Wini ini) {
        List<Vector3i> walls = scene.getWallNodeIndexList();
        int            i     = 0;
        put("Walls", "numWalls", walls.size());
        for (Vector3i nodeIndex : walls) {
            put("Wall" + i, "index.x", nodeIndex.x);
            put("Wall" + i, "index.y", nodeIndex.y);
            put("Wall" + i, "index.z", nodeIndex.z);
            i++;
        }
    }

    public void loadLights(Wini ini) {
        Vector3f vec3f;
        Vector3d vec3d;

        lights.clear();

        int numLights = getInt("Lights", "numLights", 0);

        for (int i = 0; i < numLights; i++) {
            Light light = createLight();
            light.setName(getString("Lights" + i, "name", ""));
            vec3d   = light.getPosition();
            vec3d.x = getFloat("Lights" + i, "position.x", 0);
            vec3d.y = getFloat("Lights" + i, "position.y", 10);
            vec3d.z = getFloat("Lights" + i, "position.z", 0);
            light.setPosition(vec3d);
            vec3d   = light.getRotation();
            vec3d.x = getFloat("Lights" + i, "rotation.x", 0);
            vec3d.y = getFloat("Lights" + i, "rotation.y", 0);
            vec3d.z = getFloat("Lights" + i, "rotation.z", 0);
            vec3d   = light.getDirection();
            vec3d.x = getFloat("Lights" + i, "direction.x", 0);
            vec3d.y = getFloat("Lights" + i, "direction.y", -1);
            vec3d.z = getFloat("Lights" + i, "direction.z", 0);
            vec3f   = light.getColor();
            vec3f.x = getFloat("Lights" + i, "color.x", 1);
            vec3f.y = getFloat("Lights" + i, "color.y", 1);
            vec3f.z = getFloat("Lights" + i, "color.z", 1);
            light.setColor(vec3f);

            light.setIntensity(getFloat("Lights" + i, "intensity", 1));
            light.setConstant(getFloat("Lights" + i, "constant", 1));
            light.setLinear(getFloat("Lights" + i, "linear", 1));
            light.setExponent(getFloat("Lights" + i, "exponent", 1));
            light.setScale(getFloat("Lights" + i, "scale", 1));
            light.setSpotLight(getBool("Lights" + i, "spotlight", false));
            light.setCutOff(getFloat("Lights" + i, "cutOff", 1));

            Material m = light.getSpotLightHolder().getMaterial();
            m.getAmbientColor().x = getFloat("Lights" + i, "spotLight.ambient.x", 1);
            m.getAmbientColor().y = getFloat("Lights" + i, "spotLight.ambient.y", 1);
            m.getAmbientColor().z = getFloat("Lights" + i, "spotLight.ambient.z", 1);
            m.setAmbientIntensity(getFloat("Lights" + i, "spotLight.ambientIntensity", 1));
            m.getDiffuseColor().x = getFloat("Lights" + i, "spotLight.diffuse.x", 1);
            m.getDiffuseColor().y = getFloat("Lights" + i, "spotLight.diffuse.y", 1);
            m.getDiffuseColor().z = getFloat("Lights" + i, "spotLight.ambient.z", 1);
            m.setDiffuseIntensity(getFloat("Lights" + i, "spotLight.diffuseIntensity", 1));
            m.getSpecularColor().x = getFloat("Lights" + i, "spotLight.specular.x", 1);
            m.getSpecularColor().y = getFloat("Lights" + i, "spotLight.specular.y", 1);
            m.getSpecularColor().z = getFloat("Lights" + i, "spotLight.specular.z", 1);
            m.setSpecularIntensity(getFloat("Lights" + i, "spotLight.specularIntensity", 1));
            m.setShininess(getFloat("Lights" + i, "spotLight.shininess", 1));
        }

    }

    public void saveLights(Wini ini) {
        int i = 0;
        put("Lights", "numLights", lights.size());
        for (Light light : lights) {
            put("Lights" + i, "name", light.getName());
            put("Lights" + i, "position.x", light.getPosition().x);
            put("Lights" + i, "position.y", light.getPosition().y);
            put("Lights" + i, "position.z", light.getPosition().z);
            put("Lights" + i, "rotation.x", light.getRotation().x);
            put("Lights" + i, "rotation.y", light.getRotation().y);
            put("Lights" + i, "rotation.z", light.getRotation().z);
            put("Lights" + i, "direction.x", light.getDirection().x);
            put("Lights" + i, "direction.y", light.getDirection().y);
            put("Lights" + i, "direction.z", light.getDirection().z);
            put("Lights" + i, "color.x", light.getColor().x);
            put("Lights" + i, "color.y", light.getColor().y);
            put("Lights" + i, "color.z", light.getColor().z);
            put("Lights" + i, "intensity", light.getIntensity());
            put("Lights" + i, "constant", light.getConstant());
            put("Lights" + i, "linear", light.getLinear());
            put("Lights" + i, "exponent", light.getExponent());
            put("Lights" + i, "scale", light.getScale());
            put("Lights" + i, "spotlight", light.isSpotLight());
            put("Lights" + i, "cutOff", light.getCutOff());
            Material m = light.getSpotLightHolder().getMaterial();
            put("Lights" + i, "spotLight.ambient.x", m.getAmbientColor().x);
            put("Lights" + i, "spotLight.ambient.y", m.getAmbientColor().y);
            put("Lights" + i, "spotLight.ambient.z", m.getAmbientColor().z);
            put("Lights" + i, "spotLight.ambientIntensity", m.getAmbientIntensity());
            put("Lights" + i, "spotLight.diffuse.x", m.getDiffuseColor().x);
            put("Lights" + i, "spotLight.diffuse.y", m.getDiffuseColor().y);
            put("Lights" + i, "spotLight.diffuse.z", m.getDiffuseColor().z);
            put("Lights" + i, "spotLight.diffuseIntensity", m.getDiffuseIntensity());
            put("Lights" + i, "spotLight.specular.x", m.getSpecularColor().x);
            put("Lights" + i, "spotLight.specular.y", m.getSpecularColor().y);
            put("Lights" + i, "spotLight.specular.z", m.getSpecularColor().z);
            put("Lights" + i, "spotLight.specularIntensity", m.getSpecularIntensity());
            put("Lights" + i, "spotLight.shininess", m.getShininess());
            i++;
        }
    }

    /**
     * Reset the project.
     * <p>
     * Use {@link #isChanged()} to check if reset is required.
     * If true, then define your customized reset.
     */
    @Override
    public void reset() {
        isChanged = true;
        setConstantFrequency(constantFrequency);
        slitWall.reset();
    }

    /**
     * Use this function to check if reset is required.
     * If true, then define your customized reset.
     *
     * @return true if {@link #reset()} has been called.
     */
    public boolean isChanged() {
        if (isChanged) {
            isChanged = false;
            return true;
        }
        return false;
    }

    public int getDimensionX() {
        return dimension.x;
    }

    public int getDimensionY() {
        return dimension.y;
    }

    public int getDimensionZ() {
        return dimension.z;
    }

    public void getDimension(Vector3i dimension) {
        dimension.x = this.dimension.x;
        dimension.y = this.dimension.y;
        dimension.z = this.dimension.z;
    }

    public void setDimension(Vector3i dimension) {
        this.dimension = dimension;
    }

    public void setDimension(int x, int y, int z) {
        dimension.x = x;
        dimension.y = y;
        dimension.z = z;
        reset();
    }

    public void setDimension(int x, int y, int z, boolean reset) {
        dimension.x = x;
        dimension.y = y;
        dimension.z = z;
        if (reset) reset();
    }

    public void setConstantFrequency(double value) {
        if (value > 1) value = 1;
        if (value < 0) value = 0;
        constantFrequency = value;
        constantWaveSpeed = value * value;
    }

    public void setGlSwapInterval(int glSwapInterval) {
        this.glSwapInterval = glSwapInterval;
        GLFW.glfwSwapInterval(glSwapInterval);
    }

    public Light createLight(
            Entity parent, ObjFileModel modelLightBulb, ObjFileModel modelSpotLight, Vector3d position,
            Vector3d rotation, double scale) {
        Light light = new Light(parent, modelLightBulb, modelSpotLight, position, rotation, scale);
        addLight(light);
        return light;
    }

    public Light createLight() {
        ObjFileModel modelLightBulb = new OBJFormatLoader().loadOBJModel(Constants.MODEL_LIGHT_BULB);
        ObjFileModel modelSpotLight = new OBJFormatLoader().loadOBJModel(Constants.MODEL_LIGHT_CAP);
        Light light = createLight(null, modelLightBulb, modelSpotLight,
                                  new Vector3d(0, 10, 0), new Vector3d(0, 0, 0), 1.0);
        light.set(new Vector3f(1.0f, 1.0f, 1.0f), 1.0f, 1.0f, 0.001f, 0.00001f, 60);
        light.setDirection(new Vector3d(0, -1, 0));
        return light;
    }

    public void addLight(Light light) {
        lights.add(light);
    }

}
