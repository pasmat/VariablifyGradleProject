import java.io.*;
import java.net.URI;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GradleHelper {

    private static final String BUILD_GRADLE_FILE = "build.gradle";
    private static final String EXT_PATTERN = "ext.*\\{([\\S\\s]*?)}";
    private static final String EXT_PARAMETER_PATTERN = "^[ \\t]+(.*)[ \\t]+=[ \\t]['\"](.*)['\"]$";
    private static final String DEPENDENCY_PATTERN = "[ \\t]*.*[ \\t]*['\"](.*)['\"].*$";
    private static final String GRADLE_BACKUP = "gradle_backup";


    public static void main(String[] args) {
        File rootDirectory = new File(args[0]);

        File backupFolder = new File(rootDirectory, GRADLE_BACKUP);
        if (!backupFolder.exists() && !backupFolder.mkdirs()) {
            throw new RuntimeException("Unable to create backup folder");
        }

        File rootGradleFile = new File(rootDirectory, BUILD_GRADLE_FILE);

        //TODO maybe use existign variables to compare where version is already set to variable
        //TODO but theres another instance that has hardcoded version number
        Map<String, String> variables = new HashMap<>();
        Map<String, String> variablesByGroupNames = new HashMap<>();
        Map<String, String> versionsByGroupNames = new HashMap<>();

        try {
            String rootGradle = new String(Files.readAllBytes(Paths.get(rootGradleFile.toURI())));

            Matcher extMatcher = Pattern.compile(EXT_PATTERN).matcher(rootGradle);
            if (extMatcher.find()) {
                String extSection = extMatcher.group(1);

                Matcher parameterMatcher = Pattern.compile(EXT_PARAMETER_PATTERN, Pattern.MULTILINE).matcher(extSection);


                while (parameterMatcher.find()) {
                    variables.put(parameterMatcher.group(1), parameterMatcher.group(2));
                }

                Map<File, String> gradleFilesByFile = new HashMap<>();

                Pattern dependencyPattern = Pattern.compile(DEPENDENCY_PATTERN, Pattern.MULTILINE);

                for (File moduleDirectory : rootDirectory.listFiles()) {
                    File moduleGradleFile = new File(moduleDirectory, BUILD_GRADLE_FILE);

                    if (moduleGradleFile.exists()) {
                        String moduleGradle = new String(Files.readAllBytes(Paths.get(moduleGradleFile.toURI())));

                        gradleFilesByFile.put(moduleGradleFile, moduleGradle);

                        Matcher dependencyMatcher = dependencyPattern.matcher(moduleGradle);


                        while (dependencyMatcher.find()) {
                            String dependencyString = dependencyMatcher.group(1);

                            String[] dependencyParts = dependencyString.split(":");
                            if (dependencyParts.length > 2) {
                                String version = dependencyParts[2];
                                String groupName = dependencyParts[0] + ":" + dependencyParts[1];

                                if (version.startsWith("$")) {
                                    variablesByGroupNames.put(groupName, version.substring(1));
                                } else {
                                    String preceedingVersion = versionsByGroupNames.get(groupName);
                                    if (preceedingVersion != null && !version.equals(preceedingVersion)) {
                                        throw new RuntimeException(String.format("Theres two different version for group name %s; %s and %s", groupName, preceedingVersion, version));
                                    } else {
                                        versionsByGroupNames.put(groupName, version);
                                    }
                                }
                            }
                        }
                    }
                }

                System.out.println(variablesByGroupNames.toString());
                for (String variable : variablesByGroupNames.keySet()) {
                    versionsByGroupNames.remove(variable);
                }


                String newRootGradle = insertStringAtIndex(rootGradle, extMatcher.end(1), createVersionVariables(versionsByGroupNames));
                createBackupAndApplyNewText(rootGradleFile, rootGradle, newRootGradle, new File(backupFolder, BUILD_GRADLE_FILE));



                System.out.println(newRootGradle);

                for (Map.Entry<String, String> versionByGroupName : versionsByGroupNames.entrySet()) {
                    variablesByGroupNames.put(versionByGroupName.getKey(), createVersionVariableName(versionByGroupName.getKey()));
                }

                for (Map.Entry<File, String> gradleFileByPath : gradleFilesByFile.entrySet()) {
                    String gradleFile = gradleFileByPath.getValue();

                    int index = 0;

                    StringBuilder newGradleFileStringBuilder = new StringBuilder();

                    Matcher dependencyMatcher = dependencyPattern.matcher(gradleFile);
                    while (dependencyMatcher.find()) {
                        String dependencyString = dependencyMatcher.group(1);

                        String[] dependencyParts = dependencyString.split(":");
                        if (dependencyParts.length > 2) {
                            String groupName = dependencyParts[0] + ":" + dependencyParts[1];
                            dependencyParts[2] = '$' + variablesByGroupNames.get(groupName);

                            newGradleFileStringBuilder.append(gradleFile.substring(index, dependencyMatcher.start(1) - 1));
                            newGradleFileStringBuilder.append('"');
                            newGradleFileStringBuilder.append(String.join(":", dependencyParts));
                            newGradleFileStringBuilder.append('"');
                            index = dependencyMatcher.end(1) + 1;
                        }
                    }
                    newGradleFileStringBuilder.append(gradleFile.substring(index));

                    System.out.println(gradleFileByPath.getKey());
                    System.out.println(newGradleFileStringBuilder.toString());

                    String relativePath = rootDirectory.toURI().relativize(gradleFileByPath.getKey().toURI()).toString();

                    File backupFile = new File(backupFolder, relativePath);
                    File backupFileParentFolder = backupFile.getParentFile();
                    if (!backupFileParentFolder.exists() && !backupFileParentFolder.mkdirs()) {
                        throw new RuntimeException("Unable to create backup folder");
                    }
                    createBackupAndApplyNewText(gradleFileByPath.getKey(), gradleFile, newGradleFileStringBuilder.toString(), backupFile);
                }

            } else {
                System.out.println("Define ext {} section within your root build gradle");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void createBackupAndApplyNewText(File originalFile, String originalText, String newText, File backupFile) throws IOException {
        if(!newText.equals(originalText)) {
            if(backupFile.exists()) {
                throw new RuntimeException("There's already a backup for one of the " + originalFile.getAbsolutePath() + " file maybe move it to somewhere else..?");
            }

            Files.copy(originalFile.toPath(), backupFile.toPath());

            Files.write(originalFile.toPath(), newText.getBytes());
        }
    }

    private static String insertStringAtIndex(String rootGradle, int index, String insert) {
        return rootGradle.substring(0, index) + insert + rootGradle.substring(index);
    }

    private static String createVersionVariables(Map<String, String> versionsByGroupNames) {
        StringBuilder versionVariablesStringBuilder = new StringBuilder();
        for (Map.Entry<String, String> versionByGroupName : versionsByGroupNames.entrySet()) {
            versionVariablesStringBuilder.append(createVersionVariableName(versionByGroupName.getKey()) + " = '" + versionByGroupName.getValue() + "'");
            versionVariablesStringBuilder.append('\n');
        }
        return versionVariablesStringBuilder.toString();
    }

    private static String createVersionVariableName(String groupName) {
        StringBuilder versionVariableName = new StringBuilder();

        boolean nextCamel = false;

        for (int i = 0; i < groupName.length(); i++) {
            char charAt = groupName.charAt(i);
            if (Character.isAlphabetic(charAt)) {
                versionVariableName.append(nextCamel ? Character.toUpperCase(charAt) : Character.toLowerCase(charAt));
                nextCamel = false;
            } else {
                nextCamel = true;
            }
        }
        versionVariableName.append("Version");
        return versionVariableName.toString();
    }

}
