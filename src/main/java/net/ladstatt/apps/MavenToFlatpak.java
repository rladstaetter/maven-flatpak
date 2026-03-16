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
 * * <p>This utility traverses a local Maven repository directory and generates
 * a dependency manifest compatible with Flatpak-builder. It maps local
 * files to their corresponding remote URLs on Maven Central and calculates
 * the SHA-256 checksums required for sandboxed builds.</p>
 * * <p>Requirements: Java 17 or higher (uses {@code java.util.HexFormat}).</p>
 * * @author Robert Ladstätter (rladstaetter@gmail.com)
 * @version 1.0
 */
public class MavenToFlatpak {

    private static final String MAVEN_CENTRAL = "https://repo.maven.apache.org/maven2/";

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java MavenToFlatpak <m2_dir> <output_file> [yml|json]");
            return;
        }

        Path m2Path = Paths.get(args[0]);
        Path outputPath = Paths.get(args[1]);
        String format = (args.length > 2) ? args[2].toLowerCase() : "yml";

        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(outputPath))) {
            List<Path> files = Files.walk(m2Path)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".pom") || path.toString().endsWith(".jar"))
                    .collect(Collectors.toList());

            if (format.equals("json")) {
                writeJson(writer, m2Path, files);
            } else {
                writeYaml(writer, m2Path, files);
            }

            System.out.println(format.toUpperCase() + " generated successfully at: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void writeYaml(PrintWriter writer, Path m2Path, List<Path> files) throws Exception {
        for (Path path : files) {
            String relativePath = getRelativePath(m2Path, path);
            writer.println("- type: file");
            writer.println("  url: " + MAVEN_CENTRAL + relativePath);
            writer.println("  sha256: " + calculateSHA256(path));
            writer.println("  dest: ./offline-repository/" + getDirOnly(relativePath));
            writer.println("  dest-filename: " + path.getFileName());
            writer.println();
        }
    }

    private static void writeJson(PrintWriter writer, Path m2Path, List<Path> files) throws Exception {
        writer.println("[");
        for (int i = 0; i < files.size(); i++) {
            Path path = files.get(i);
            String relativePath = getRelativePath(m2Path, path);
            writer.println("  {");
            writer.println("    \"type\": \"file\",");
            writer.println("    \"url\": \"" + MAVEN_CENTRAL + relativePath + "\",");
            writer.println("    \"sha256\": \"" + calculateSHA256(path) + "\",");
            writer.println("    \"dest\": \"./offline-repository/" + getDirOnly(relativePath) + "\",");
            writer.println("    \"dest-filename\": \"" + path.getFileName() + "\"");
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
        // For very large repos, use a BufferedInputStream to avoid OOM
        byte[] hash = digest.digest(Files.readAllBytes(path));
        return HexFormat.of().formatHex(hash);
    }
}