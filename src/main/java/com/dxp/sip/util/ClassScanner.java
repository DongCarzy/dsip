package com.dxp.sip.util;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 包扫描
 *
 * @author dongxinping
 */
public class ClassScanner {

    /**
     * 在文件夹中扫描包和类
     */
    private static void doScanPackageClassesByFile(Set<Class<?>> classes, String packageName, String packagePath) throws ClassNotFoundException {
        // 转为文件
        File dir = new File(packagePath);
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }

        // 列出文件，进行过滤
        File[] dirFiles = dir.listFiles();
        for (File file : dirFiles) {
            if (file.isDirectory()) {
                // 如果是目录，则递归
                doScanPackageClassesByFile(classes, packageName + "." + file.getName(), file.getAbsolutePath());
            } else {
                // 用当前类加载器加载 去除 fileName 的 .class 6 位
                String className = file.getName().substring(0, file.getName().length() - 6);
                Class<?> loadClass = Thread.currentThread().getContextClassLoader().loadClass("$packageName.$className");
                classes.add(loadClass);
            }
        }
    }

    /**
     * Do scan all classes set.
     *
     * @return the set
     */
    public static Set<Class<?>> doScanAllClasses(String packageName) throws ClassNotFoundException, IOException {
        String packages = packageName;
        Set<Class<?>> classes = new LinkedHashSet<>();

        // 如果最后一个字符是“.”，则去掉
        if (packages.endsWith(".")) {
            packages = packages.substring(0, packages.lastIndexOf('.'));
        }

        // 将包名中的“.”换成系统文件夹的“/”
        String basePackageFilePath = packages.replace('.', '/');
        Enumeration<URL> resources = Thread.currentThread().getContextClassLoader().getResources(basePackageFilePath);
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            String protocol = resource.getProtocol();
            if ("file".equals(protocol)) {
                String filePath = URLDecoder.decode(resource.getFile(), "UTF-8");
                // 扫描文件夹中的包和类
                doScanPackageClassesByFile(classes, packages, filePath);
            }
        }
        return classes;
    }
}
