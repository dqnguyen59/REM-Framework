package remframework.lattice;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public record LatticeDescriptor(String identifier, String displayName, SourceType sourceType,
                                Path sourcePath, boolean valid,
                                List<ValidationMessage> messages,
                                Map<String, Object> metadata, LatticeModel model) {
    public enum SourceType { SYSTEM, FILE }

    public boolean generatedTemplate() {
        return Boolean.TRUE.equals(metadata.get("systemTemplate"));
    }

    public String sourceLabel() {
        if (sourceType == SourceType.SYSTEM) return "Built-in";
        if (generatedTemplate()) return "Generated";
        return sourcePath == null ? "File" : sourcePath.getFileName().toString();
    }

    public String uiLabel() {
        return (valid ? "" : "[!] ") + displayName + " - " + sourceLabel()
                + (valid ? "" : " - Invalid");
    }
}
