package remframework.settings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.ini4j.Wini;
import org.joml.Vector3i;
import remframework.lattice.LatticeManager;

/** Reproducible equilibrium experiment setup; results and evolved node states are separate. */
public final class DiamondExperimentPreset {
    private DiamondExperimentPreset() {}

    public static void save(Path path,AppSettings settings,Project project) throws IOException {
        if(!project.scene.getLattice().isMechanicsOnly())throw new IOException("Select diamond mechanics first");
        Files.createDirectories(path.toAbsolutePath().getParent());
        settings.rememberDimensions(new Vector3i(project.getDimensionX(),project.getDimensionY(),project.getDimensionZ()));
        var ini=new Wini();settings.saveToFile(ini,"",0);project.saveToFile(ini,"",0);
        var lattice=project.scene.getLattice();
        ini.put("DiamondExperiment","version",1);
        ini.put("DiamondExperiment","transport",lattice.isDiamondTransportEnabled());
        ini.put("DiamondExperiment","pressure",lattice.isDiamondPressureEnabled());
        ini.put("DiamondExperiment","identityExcitation",lattice.isDiamondIdentityExcitationEnabled());
        ini.store(path.toFile());
        String name="experiment-"+System.currentTimeMillis()+".ini";
        Files.copy(path,path.resolveSibling(name),StandardCopyOption.COPY_ATTRIBUTES);
    }

    public static void load(Path path,AppSettings settings,Project project) throws IOException {
        var ini=new Wini(path.toFile());
        if(!"1".equals(ini.get("DiamondExperiment","version")))throw new IOException("Unsupported experiment preset");
        if(!LatticeManager.EXPERIMENTAL_DIAMOND_ID.equals(ini.get("System","latticeModel")))
            throw new IOException("Preset must select experimental diamond mechanics");
        Vector3i dimensions;
        try {
            dimensions=new Vector3i(Integer.parseInt(ini.get("QuantumField","dimension.x")),
                Integer.parseInt(ini.get("QuantumField","dimension.y")),Integer.parseInt(ini.get("QuantumField","dimension.z")));
            if(!remframework.lattice.ExperimentalDiamondLatticeModel.validDimensions(dimensions))throw new IllegalArgumentException();
            double k=Double.parseDouble(ini.get("QuantumField","constantFrequency"));
            double gamma=Double.parseDouble(ini.get("QuantumField","constantElectricFactor"));
            if(!Double.isFinite(k)||!Double.isFinite(gamma)||k<=0||gamma<=0||gamma*k*k>1)throw new IllegalArgumentException();
        } catch(RuntimeException invalid){throw new IOException("Invalid dimensions or mechanics in preset",invalid);}
        boolean reuse=project.getRemModel().isLoadingReady() && project.scene.getLattice().isMechanicsOnly()
            && project.scene.getLattice().getNodeArray()!=null
            && project.getDimensionX()==dimensions.x && project.getDimensionY()==dimensions.y && project.getDimensionZ()==dimensions.z;
        project.getRemModel().stopSimulation();project.scene.getLattice().getDisturbance().cancel();
        settings.loadFromFile(ini,"",0);
        LatticeManager.getInstance().select(settings.getLatticeModelIdentifier());
        project.loadExperimentPreset(ini,reuse);
        var lattice=project.scene.getLattice();
        lattice.setDiamondTransportEnabled(Boolean.parseBoolean(ini.get("DiamondExperiment","transport")));
        lattice.setDiamondPressureEnabled(Boolean.parseBoolean(ini.get("DiamondExperiment","pressure")));
        lattice.setDiamondIdentityExcitationEnabled(Boolean.parseBoolean(ini.get("DiamondExperiment","identityExcitation")));
        if(!reuse)settings.requestLatticeRebuild();
        if(!settings.getCurrentFilename().isEmpty())settings.saveToFile();
    }
}
