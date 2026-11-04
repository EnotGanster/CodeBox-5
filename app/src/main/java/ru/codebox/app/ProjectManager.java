package ru.codebox.app;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

public final class ProjectManager {

    private final File projectsDirectory;

    public ProjectManager(Context context) {
        projectsDirectory = new File(
                context.getFilesDir(), "projects"
        );
    }

    public File getProjectsDirectory() {
        return projectsDirectory;
    }

    public File[] getProjects() {
        File[] projects = projectsDirectory.listFiles(
                file -> file.isDirectory()
                        && !file.getName().startsWith(".")
        );

        return projects != null ? projects : new File[0];
    }

    public File createProject(String projectName)
            throws IOException {

        if (projectName == null
                || !projectName.matches(
                        "[a-zA-Z][a-zA-Z0-9_]{0,49}"
                )) {
            throw new IOException(
                    "Название: латинские буквы, цифры и _"
            );
        }

        FileUtils.createDirectory(projectsDirectory);

        File project = new File(
                projectsDirectory, projectName
        );

        if (project.exists()) {
            throw new IOException(
                    "Проект с таким именем уже существует"
            );
        }

        String packageName =
                ProjectTemplates.getPackageName(projectName);

        File javaDirectory = new File(
                project,
                "app/src/main/java/"
                        + packageName.replace('.', '/')
        );

        File layoutDirectory = new File(
                project, "app/src/main/res/layout"
        );

        try {
            FileUtils.createDirectory(project);
            FileUtils.createDirectory(javaDirectory);
            FileUtils.createDirectory(layoutDirectory);

            FileUtils.writeText(
                    new File(project, "settings.gradle"),
                    ProjectTemplates.settingsGradle(projectName)
            );

            FileUtils.writeText(
                    new File(project, "build.gradle"),
                    ProjectTemplates.rootBuildGradle()
            );

            FileUtils.writeText(
                    new File(project, "gradle.properties"),
                    ProjectTemplates.gradleProperties()
            );

            FileUtils.writeText(
                    new File(project, ".gitignore"),
                    ProjectTemplates.gitignore()
            );

            FileUtils.writeText(
                    new File(project, "app/build.gradle"),
                    ProjectTemplates.appBuildGradle(packageName)
            );

            FileUtils.writeText(
                    new File(
                            project,
                            "app/src/main/AndroidManifest.xml"
                    ),
                    ProjectTemplates.manifest(projectName)
            );

            FileUtils.writeText(
                    new File(
                            javaDirectory, "MainActivity.java"
                    ),
                    ProjectTemplates.mainActivity(
                            packageName, projectName
                    )
            );

            return project;

        } catch (IOException e) {
            deleteIncompleteProject(project);
            throw e;
        }
    }

    private void deleteIncompleteProject(File file) {
        if (file == null || !file.exists()) {
            return;
        }

        if (file.isDirectory()) {
            File[] children = file.listFiles();

            if (children != null) {
                for (File child : children) {
                    deleteIncompleteProject(child);
                }
            }
        }

        file.delete();
    }
}
