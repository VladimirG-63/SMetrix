import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ProjectExporter {

    // Папки, которые нам точно не нужно экспортировать (чтобы не забить файл мусором)
    private static final Set<String> IGNORED_DIRS = Set.of(
            "build", ".gradle", ".idea", ".git", "gradle"
    );

    // Форматы файлов, исходный код которых мы хотим видеть в отчете
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".java", ".xml", ".gradle", ".md", ".properties"
    );

    public static void main(String[] args) {
        Path rootPath = Paths.get(".");
        Path outputPath = Paths.get("PROJECT_STRUCTURE.md");

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
            writer.write("# 🏗️ АРХИТЕКТУРА И ИСХОДНЫЙ КОД ПРОЕКТА\n\n");
            writer.write("> Сгенерировано: " + LocalDateTime.now() + "\n\n");

            // 1. Генерируем дерево папок (чтобы анализатор понимал структуру)
            writer.write("## 📂 СТРУКТУРА ПАПОК\n```text\n");
            generateTree(rootPath, "", writer);
            writer.write("```\n\n");

            // 2. Читаем и записываем содержимое всех файлов через Stream API
            writer.write("## 💻 ИСХОДНЫЙ КОД\n\n");

            try (Stream<Path> paths = Files.walk(rootPath)) {
                paths.filter(Files::isRegularFile)
                        .filter(ProjectExporter::isAllowedFile)
                        .filter(path -> !path.equals(outputPath)) // Исключаем сам файл отчета
                        .forEach(path -> appendFileContent(rootPath, path, writer));
            }

            System.out.println("✅ Экспорт успешно завершен! Файл PROJECT_STRUCTURE.md обновлен.");

        } catch (IOException e) {
            System.err.println("❌ Ошибка при экспорте проекта: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static boolean isAllowedFile(Path path) {
        String pathString = path.toString().replace("\\", "/");

        // Проверяем, не лежит ли файл в игнорируемой папке
        boolean inIgnoredDir = IGNORED_DIRS.stream()
                .anyMatch(dir -> pathString.contains("/" + dir + "/") || pathString.startsWith(dir + "/"));

        if (inIgnoredDir) return false;

        // Проверяем расширение файла
        return ALLOWED_EXTENSIONS.stream().anyMatch(pathString::endsWith);
    }

    private static void appendFileContent(Path rootPath, Path filePath, BufferedWriter writer) {
        try {
            String relativePath = rootPath.relativize(filePath).toString().replace("\\", "/");
            writer.write("### 📄 " + relativePath + "\n");

            String extension = relativePath.substring(relativePath.lastIndexOf('.'));
            writer.write("```" + getMarkdownLanguage(extension) + "\n");

            // Читаем весь файл и пишем в отчет
            String content = Files.readString(filePath);
            writer.write(content);

            if (!content.endsWith("\n")) {
                writer.write("\n");
            }
            writer.write("```\n\n---\n\n");
        } catch (IOException e) {
            System.err.println("Не удалось прочитать файл: " + filePath);
        }
    }

    private static String getMarkdownLanguage(String extension) {
        return switch (extension) {
            case ".java" -> "java";
            case ".xml" -> "xml";
            case ".gradle" -> "groovy";
            case ".md" -> "markdown";
            case ".properties" -> "properties";
            default -> "text";
        };
    }

    private static void generateTree(Path dir, String indent, BufferedWriter writer) throws IOException {
        File[] files = dir.toFile().listFiles();
        if (files == null) return;

        // Сортируем: сначала папки, потом файлы
        var sortedFiles = Stream.of(files)
                .filter(f -> !IGNORED_DIRS.contains(f.getName()))
                .sorted((f1, f2) -> {
                    if (f1.isDirectory() && !f2.isDirectory()) return -1;
                    if (!f1.isDirectory() && f2.isDirectory()) return 1;
                    return f1.getName().compareToIgnoreCase(f2.getName());
                })
                .collect(Collectors.toList());

        for (int i = 0; i < sortedFiles.size(); i++) {
            File file = sortedFiles.get(i);
            boolean isLast = (i == sortedFiles.size() - 1);
            writer.write(indent + (isLast ? "└── " : "├── ") + file.getName() + "\n");

            if (file.isDirectory()) {
                generateTree(file.toPath(), indent + (isLast ? "    " : "│   "), writer);
            }
        }
    }
}