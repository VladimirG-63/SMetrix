import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ProjectDiffReporter {
    public static void main(String[] args) {
        String reportFile = "CHANGES_REPORT.md";

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(reportFile, StandardCharsets.UTF_8))) {
            writer.write("# 📝 ОТЧЕТ ОБ ИЗМЕНЕНИЯХ\n\n");
            writer.write("> Дата: " + new Date() + "\n\n");

            List<String> modifiedFiles = executeCommand("git", "diff", "--name-only");
            List<String> untrackedFiles = executeCommand("git", "ls-files", "--others", "--exclude-standard");

            if (modifiedFiles.isEmpty() && untrackedFiles.isEmpty()) {
                writer.write("### ✅ Изменений не обнаружено.\n");
            } else {
                writer.write("## 🛠 Список измененных файлов\n");
                for (String f : modifiedFiles) writer.write("- ` " + f + " ` (изменен)\n");
                for (String f : untrackedFiles) writer.write("- ` " + f + " ` (новый)\n");

                writer.write("\n---\n\n");

                for (String file : modifiedFiles) {
                    writer.write("### 📄 Diff: `" + file + "`\n");
                    writer.write("```diff\n");
                    for (String line : executeCommand("git", "diff", "HEAD", file)) {
                        writer.write(line + "\n");
                    }
                    writer.write("```\n\n");
                }

                for (String file : untrackedFiles) {
                    writer.write("### 🆕 Новый файл: `" + file + "`\n");
                    writer.write("```java\n");
                    try {
                        writer.write(Files.readString(Path.of(file)));
                    } catch (Exception e) {
                        writer.write("// Не удалось прочитать содержимое");
                    }
                    writer.write("\n```\n\n");
                }
            }
            System.out.println("Отчет об изменениях готов: " + reportFile);
        } catch (IOException e) {
            System.err.println("Ошибка: " + e.getMessage());
        }
    }

    private static List<String> executeCommand(String... command) {
        List<String> output = new ArrayList<>();
        try {
            Process process = new ProcessBuilder(command).start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) output.add(line);
            }
            process.waitFor();
        } catch (Exception ignored) {}
        return output;
    }
}