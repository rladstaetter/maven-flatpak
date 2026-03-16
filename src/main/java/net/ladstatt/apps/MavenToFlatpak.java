package net.ladstatt.apps;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

/**
 * MavenToFlatpak Generator
 * <p>This utility traverses a local Maven repository directory and generates
 * a dependency manifest compatible with Flatpak-builder.</p>
 *
 * @author Robert Ladstätter (rladstaetter@gmail.com)
 * @version 1.2
 */
public class MavenToFlatpak {

    private static final String MAVEN_CENTRAL = "https://repo.maven.apache.org/maven2/";

    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage: java MavenToFlatpak <m2_dir> <output_file> <arch>");
            System.out.println("Example: java MavenToFlatpak ./temp-repo deps.json aarch64");
            return;
        }

        Path m2Path = Paths.get(args[0]);
        Path outputPath = Paths.get(args[1]);
        String arch = args[2];
        boolean isJson = outputPath.toString().toLowerCase().endsWith(".json");

        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(outputPath))) {
            List<Path> files = Files.walk(m2Path)
                    .filter(Files::isRegularFile)
                    .filter(path -> !isLocalOnlyMetadata(path))
                    .collect(Collectors.toList());

            if (isJson) {
                writeJson(writer, m2Path, files, arch);
            } else {
                writeYaml(writer, m2Path, files, arch);
            }

            System.out.println("Generated " + (isJson ? "JSON" : "YAML") + " manifest for " + arch + " with " + files.size() + " files.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean isLocalOnlyMetadata(Path path) {
        String filename = path.getFileName().toString();
        String pathString = path.toString().replace("\\", "/");

        return filename.endsWith(".lastUpdated") ||
                filename.endsWith(".sha1") ||
                filename.endsWith(".md5") ||
                filename.startsWith(".") ||
                pathString.contains("/.meta/") ||
                filename.equals("_remote.repositories") ||
                filename.equals("_maven.repositories") ||
                filename.equals("maven-metadata-local.xml") ||
                filename.equals("resolver-status.properties");
    }

    private static void writeYaml(PrintWriter writer, Path m2Path, List<Path> files, String arch) throws Exception {
        for (Path path : files) {
            String relativePath = getRelativePath(m2Path, path);
            String filename = path.getFileName().toString();
            String remotePath = relativePath.replace("-central", "");

            writer.println("- type: file");
            writer.println("  url: " + MAVEN_CENTRAL + remotePath);
            writer.println("  sha256: " + calculateSHA256(path));
            writer.println("  dest: offline-repository/" + getDirOnly(relativePath));
            writer.println("  dest-filename: " + filename);
            writer.println("  only-arches: [" + arch + "]");
            writer.println();
        }
    }

    private static void writeJson(PrintWriter writer, Path m2Path, List<Path> files, String arch) throws Exception {
        writer.println("[");
        for (int i = 0; i < files.size(); i++) {
            Path path = files.get(i);
            String relativePath = getRelativePath(m2Path, path);
            String filename = path.getFileName().toString();
            String remotePath = relativePath.replace("-central", "");

            writer.println("  {");
            writer.println("    \"type\": \"file\",");
            writer.println("    \"url\": \"" + MAVEN_CENTRAL + remotePath + "\",");
            writer.println("    \"sha256\": \"" + calculateSHA256(path) + "\",");
            writer.println("    \"dest\": \"offline-repository/" + getDirOnly(relativePath) + "\",");
            writer.println("    \"dest-filename\": \"" + filename + "\",");
            writer.println("    \"only-arches\": [ \"" + arch + "\" ]");
            writer.print("  }" + (i < files.size() - 1 ? "," : ""));
            writer.println();
        }
        writer.println("]");
    }

    private static String getRelativePath(Path m2Path, Path path) {
        return m2Path.relativize(path).toString().replace("\\", "/");
    }

    private static String getDirOnly(String relativePath) {
        int lastSlash = relativePath.lastIndexOf('/');
        return (lastSlash == -1) ? "" : relativePath.substring(0, lastSlash);
    }

    private static String calculateSHA256(Path path) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(Files.readAllBytes(path));
        return HexFormat.of().formatHex(hash);
    }
}