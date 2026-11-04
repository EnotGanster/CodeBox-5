package ru.codebox.app;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class ProjectTreeManager {

    private static final int MAX_DEPTH = 40;
    private static final int MAX_ENTRIES = 10000;

    private ProjectTreeManager() {
    }

    public static String buildTree(File project)
            throws IOException {

        if (project == null) {
            throw new IOException("Проект не выбран");
        }

        File root = project.getCanonicalFile();

        if (!root.isDirectory()) {
            throw new IOException(
                    "Папка проекта не найдена"
            );
        }

        StringBuilder result = new StringBuilder();

        result.append("📁 ")
                .append(root.getName())
                .append("/\n");

        Set<String> visited = new HashSet<>();
        visited.add(root.getCanonicalPath());

        int[] counter = {0};

        appendDirectory(
                root,
                root,
                "",
                0,
                result,
                visited,
                counter
        );

        return result.toString();
    }

    private static void appendDirectory(
            File root,
            File directory,
            String prefix,
            int depth,
            StringBuilder result,
            Set<String> visited,
            int[] counter) throws IOException {

        if (depth >= MAX_DEPTH) {
            result.append(prefix)
                    .append("└── … Максимальная глубина\n");
            return;
        }

        File[] children = directory.listFiles();

        if (children == null) {
            result.append(prefix)
                    .append("└── [Нет доступа]\n");
            return;
        }

        Arrays.sort(children, (a, b) -> {
            if (a.isDirectory() != b.isDirectory()) {
                return a.isDirectory() ? -1 : 1;
            }

            return a.getName()
                    .compareToIgnoreCase(b.getName());
        });

        for (int i = 0; i < children.length; i++) {

            if (counter[0] >= MAX_ENTRIES) {
                result.append(prefix)
                        .append("└── … Список ограничен\n");
                return;
            }

            File child = children[i];
            boolean last = i == children.length - 1;

            String branch = last ? "└── " : "├── ";

            String canonical = child.getCanonicalPath();
            String rootPath = root.getCanonicalPath();

            if (!canonical.startsWith(
                    rootPath + File.separator
            )) {
                result.append(prefix)
                        .append(branch)
                        .append("[Пропущен небезопасный путь]\n");
                continue;
            }

            boolean folder = child.isDirectory();

            result.append(prefix)
                    .append(branch)
                    .append(folder ? "📁 " : "📄 ")
                    .append(child.getName());

            if (folder) {
                result.append("/");
            }

            result.append("\n");
            counter[0]++;

            if (folder) {
                if (!visited.add(canonical)) {
                    result.append(prefix)
                            .append(last ? "    " : "│   ")
                            .append("└── [Повторная ссылка]\n");
                    continue;
                }

                appendDirectory(
                        root,
                        child,
                        prefix + (
                                last ? "    " : "│   "
                        ),
                        depth + 1,
                        result,
                        visited,
                        counter
                );
            }
        }
    }
}
