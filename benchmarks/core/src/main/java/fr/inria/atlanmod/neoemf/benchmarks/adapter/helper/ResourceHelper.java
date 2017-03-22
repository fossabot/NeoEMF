/*
 * Copyright (c) 2013-2017 Atlanmod INRIA LINA Mines Nantes.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Atlanmod INRIA LINA Mines Nantes - initial API and implementation
 */

package fr.inria.atlanmod.neoemf.benchmarks.adapter.helper;

import fr.inria.atlanmod.neoemf.benchmarks.adapter.InternalAdapter;
import fr.inria.atlanmod.neoemf.util.log.Log;

import org.eclipse.emf.common.util.BasicEList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.XMIResource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static fr.inria.atlanmod.neoemf.util.Preconditions.checkArgument;
import static fr.inria.atlanmod.neoemf.util.Preconditions.checkNotNull;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/**
 * A class that provides static methods for {@link Resource} management.
 */
public final class ResourceHelper {

    private static final String XMI = "xmi";
    private static final String ZXMI = "zxmi";

    /**
     * The name of the default ZIP file.
     */
    private static final String ZIP_FILENAME = "resources.zip";

    /**
     * A {@link Map} that holds all available resources in {@link #ZIP_FILENAME}.
     */
    private static List<String> AVAILABLE_RESOURCES;

    /**
     * A {@link Map} that holds all registered resources.
     */
    private static Map<String, String> REGISTERED_RESOURCES;

    private ResourceHelper() {
    }

    /**
     * Checks that the resource, identified by its {@code filename}, is valid, i.e. if its extension is recognized.
     *
     * @param filename the name of the resource file
     *
     * @throws IllegalArgumentException if the resource is not valid
     */
    private static void checkValidResource(String filename) {
        checkNotNull(filename);

        checkArgument(filename.endsWith("." + XMI) || filename.endsWith("." + ZXMI),
                "'%s' is an invalid resource file. Only *.%s and *.%s files are allowed.", filename, XMI, ZXMI);
    }

    /**
     * Copies the given {@code sourceFile} to the temporary directory.
     *
     * @param sourceFile the file to copy
     *
     * @return the created file
     *
     * @throws IOException if an I/O error occurs during the copy
     */
    public static File copyStore(File sourceFile) throws IOException {
        Path outputFile = Workspace.newTempDirectory().resolve(sourceFile.getName());

        Log.info("Copy {0} to {1}", sourceFile, outputFile);

        Files.walkFileTree(sourceFile.toPath(), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path targetPath = outputFile.resolve(sourceFile.toPath().relativize(dir));
                if (!Files.exists(targetPath)) {
                    Files.createDirectory(targetPath);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, outputFile.resolve(sourceFile.toPath().relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });

        return outputFile.toFile();
    }

    /**
     * Creates a new {@link Resource} (a {@link fr.inria.atlanmod.neoemf.resource.PersistentResource} in case of NeoEMF)
     * from the given {@code sourceFile}, and stores it to the given {@code targetAdapter}, located in a temporary
     * directory.
     *
     * @param sourceFile    the resource file
     * @param targetAdapter the adapter where to store the resource
     *
     * @return the created file
     *
     * @throws Exception if a error occurs during the creation of the store
     * @see Workspace#newTempDirectory()
     */
    public static File createTempStore(File sourceFile, InternalAdapter targetAdapter) throws Exception {
        return createStore(sourceFile, targetAdapter, Workspace.newTempDirectory());
    }

    /**
     * Creates a new {@link Resource} (a {@link fr.inria.atlanmod.neoemf.resource.PersistentResource} in case of NeoEMF)
     * from the given {@code sourceFile}, and stores it to the given {@code targetAdapter}, located in the workspace.
     *
     * @param sourceFile    the resource file
     * @param targetAdapter the adapter where to store the resource
     *
     * @return the created file
     *
     * @throws Exception if a error occurs during the creation of the store
     * @see Workspace#getStoreDirectory()
     */
    public static File createStore(File sourceFile, InternalAdapter targetAdapter) throws Exception {
        return createStore(sourceFile, targetAdapter, Workspace.getStoreDirectory());
    }

    /**
     * Creates a new {@link Resource} (a {@link fr.inria.atlanmod.neoemf.resource.PersistentResource} in case of NeoEMF)
     * from the given {@code sourceFile}, and stores it to the given {@code targetAdapter}, located in {@code
     * targetDir}.
     *
     * @param sourceFile    the resource file
     * @param targetAdapter the adapter where to store the resource
     * @param targetDir     the location of the adapter
     *
     * @return the created file
     *
     * @throws Exception if a error occurs during the creation of the store
     */
    private static File createStore(File sourceFile, InternalAdapter targetAdapter, Path targetDir) throws Exception {
        checkValidResource(sourceFile.getName());
        checkArgument(sourceFile.exists(), "Resource '%s' does not exist", sourceFile);

        String targetFileName = getNameWithoutExtension(sourceFile.getAbsolutePath()) + "." + targetAdapter.getStoreExtension();
        File targetFile = targetDir.resolve(targetFileName).toFile();

        if (targetFile.exists()) {
            Log.info("Already existing store {0}", targetFile);
            return targetFile;
        }

        ResourceSet resourceSet = loadResourceSet();

        URI sourceUri = URI.createFileURI(sourceFile.getAbsolutePath());

        Resource sourceResource = resourceSet.createResource(sourceUri);

        targetAdapter.initAndGetEPackage();

        Log.info("Loading '{0}'", sourceUri);
        Map<String, Object> loadOpts = new HashMap<>();
        if (Objects.equals(ZXMI, sourceUri.fileExtension())) {
            loadOpts.put(XMIResource.OPTION_ZIP, Boolean.TRUE);
        }
        sourceResource.load(loadOpts);

        Log.info("Migrating");

        Resource targetResource = targetAdapter.createResource(targetFile, resourceSet);
        targetAdapter.save(targetResource);

        targetResource.getContents().addAll(EcoreUtil.copyAll(sourceResource.getContents()));

        sourceResource.unload();

        Log.info("Saving to '{0}'", targetResource.getURI());
        targetAdapter.save(targetResource);

        targetAdapter.unload(targetResource);

        return targetFile;
    }

    /**
     * Creates a new {@link Resource} from the given {@code sourceFilename}, and adapts it for the given
     * {@code targetAdapter}. The resource file can be placed in the resource ZIP, or in the file system.
     *
     * @param sourceFilename the name of the resource file
     * @param targetAdapter  the adapter where to store the resource
     *
     * @return the created file
     *
     * @throws Exception if a error occurs during the creation of the resource
     */
    public static File createResource(String sourceFilename, InternalAdapter targetAdapter) throws Exception {
        if (getRegisteredResources().containsKey(sourceFilename.toLowerCase())) {
            sourceFilename = getRegisteredResources().get(sourceFilename.toLowerCase());
        }

        File sourceFile;
        if (getZipResources().contains(sourceFilename)) {
            // Get file from the resources/resource.zip
            sourceFile = extractFromZip(sourceFilename, Workspace.getResourcesDirectory());
        }
        else {
            // Get the file from the file system
            sourceFile = new File(sourceFilename);
        }

        checkValidResource(sourceFile.getName());
        checkArgument(sourceFile.exists(), "Resource '%s' does not exist", sourceFile);
        return createResource(sourceFile, targetAdapter);
    }

    /**
     * Creates a new {@link Resource} from the given {@code file}, and adapts it for the given {@code targetAdapter}.
     *
     * @param sourceFile    the resource file
     * @param targetAdapter the adapter where to store the resource
     *
     * @return the created file
     *
     * @throws Exception if a error occurs during the creation of the resource
     */
    private static File createResource(File sourceFile, InternalAdapter targetAdapter) throws Exception {
        String targetFileName = getNameWithoutExtension(sourceFile.getName()) + "." + targetAdapter.getResourceExtension() + "." + ZXMI;
        File targetFile = Workspace.getResourcesDirectory().resolve(targetFileName).toFile();

        if (targetFile.exists()) {
            Log.info("Already existing resource {0}", targetFile);
            return targetFile;
        }

        ResourceSet resourceSet = loadResourceSet();

        URI sourceURI = URI.createFileURI(sourceFile.getAbsolutePath());

        Log.info("Loading '{0}'", sourceURI);
        Resource sourceResource = resourceSet.getResource(sourceURI, true);

        Log.info("Migrating");

        Map<String, Object> saveOpts = new HashMap<>();
        saveOpts.put(XMIResource.OPTION_ZIP, Boolean.TRUE);

        URI targetURI = URI.createFileURI(targetFile.getAbsolutePath());
        Resource targetResource = resourceSet.createResource(targetURI);

        targetResource.getContents().add(migrate(sourceResource.getContents().get(0), targetAdapter.initAndGetEPackage()));

        sourceResource.unload();

        Log.info("Saving to '{0}'", targetResource.getURI());

        targetResource.save(saveOpts);

        targetResource.unload();

        return targetFile;
    }

    /*
     * EMF migration
     */

    /**
     * Creates a new pre-configured {@link ResourceSet} able to handle registered extensions.
     *
     * @return a new {@link ResourceSet}
     */
    private static ResourceSet loadResourceSet() {
        org.eclipse.gmt.modisco.java.emf.impl.JavaPackageImpl.init();

        ResourceSet resourceSet = new ResourceSetImpl();
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put(XMI, new XMIResourceFactoryImpl());
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put(ZXMI, new XMIResourceFactoryImpl());
        return resourceSet;
    }

    /**
     * Adapts the given {@code object} in a particular implementation, specified by the {@code targetPackage}.
     *
     * @param object        the root {@link EObject} to adapt
     * @param targetPackage the {@link EPackage}
     *
     * @return the adapted {@code object}
     */
    private static EObject migrate(EObject object, EPackage targetPackage) {
        Map<EObject, EObject> correspondences = new HashMap<>();
        EObject adaptedObject = getCorrespondingEObject(correspondences, object, targetPackage);
        copy(correspondences, object, adaptedObject);

        Iterable<EObject> allContents = () -> EcoreUtil.getAllContents(object, true);

        for (EObject sourceEObject : allContents) {
            EObject targetEObject = getCorrespondingEObject(correspondences, sourceEObject, targetPackage);
            copy(correspondences, sourceEObject, targetEObject);
        }

        return adaptedObject;
    }

    /**
     * Copies the {@code sourceObject} to the {@code targetObject}, by using the {@code correspondences} {@link Map}.
     *
     * @param correspondences the {@link Map} holding the link between the original {@link EObject} and its adaptation
     * @param sourceObject    the source {@link EObject}
     * @param targetObject    the corresponding {@link EObject}
     *
     * @see #getCorrespondingEObject(Map, EObject, EPackage)
     */
    private static void copy(Map<EObject, EObject> correspondences, EObject sourceObject, EObject targetObject) {
        for (EStructuralFeature sourceFeature : sourceObject.eClass().getEAllStructuralFeatures()) {
            if (sourceObject.eIsSet(sourceFeature)) {
                EStructuralFeature targetFeature = targetObject.eClass().getEStructuralFeature(sourceFeature.getName());
                if (sourceFeature instanceof EAttribute) {
                    targetObject.eSet(targetFeature, sourceObject.eGet(sourceFeature));
                }
                else { // EReference
                    if (!sourceFeature.isMany()) {
                        targetObject.eSet(targetFeature, getCorrespondingEObject(correspondences, (EObject) sourceObject.eGet(targetFeature), targetObject.eClass().getEPackage()));
                    }
                    else {
                        List<EObject> targetList = new BasicEList<>();

                        @SuppressWarnings({"unchecked"})
                        Iterable<EObject> sourceList = (Iterable<EObject>) sourceObject.eGet(sourceFeature);
                        for (EObject aSourceList : sourceList) {
                            targetList.add(getCorrespondingEObject(correspondences, aSourceList, targetObject.eClass().getEPackage()));
                        }
                        targetObject.eSet(targetFeature, targetList);
                    }
                }
            }
        }
    }

    /**
     * Adapts the given {@code object} in a particular implementation, specified by the {@code ePackage}, and stores the
     * correspondence in the given {@code correspondences} {@link Map}.
     *
     * @param correspondences the {@link Map} where to store the link between the original {@link EObject} and its
     *                        adaptation
     * @param object          the {@link EObject} to adapt
     * @param ePackage        the {@link EPackage} used to retrieve the corresponding {@link EObject}
     *
     * @return the corresponding {@link EObject}
     */
    private static EObject getCorrespondingEObject(Map<EObject, EObject> correspondences, EObject object, EPackage ePackage) {
        EObject correspondingObject = correspondences.get(object);
        if (isNull(correspondingObject)) {
            EClass eClass = object.eClass();
            EClass targetClass = (EClass) ePackage.getEClassifier(eClass.getName());
            correspondingObject = EcoreUtil.create(targetClass);
            correspondences.put(object, correspondingObject);
        }
        return correspondingObject;
    }

    /*
     * ZIP extraction
     */

    /**
     * Returns all resources contained in the default ZIP file.
     *
     * @return a {@link List} of the file name of the resources
     *
     * @throws IOException if the ZIP file cannot be found
     */
    private static List<String> getZipResources() throws IOException {
        if (isNull(AVAILABLE_RESOURCES)) {
            AVAILABLE_RESOURCES = new ArrayList<>();

            try (ZipInputStream inputStream = new ZipInputStream(ResourceHelper.class.getResourceAsStream("/" + ZIP_FILENAME))) {
                ZipEntry entry = inputStream.getNextEntry();
                while (nonNull(entry)) {
                    if (!entry.isDirectory()) {
                        checkValidResource(entry.getName());
                        AVAILABLE_RESOURCES.add(new File(entry.getName()).getName());
                    }
                    inputStream.closeEntry();
                    entry = inputStream.getNextEntry();
                }
            }
        }
        return AVAILABLE_RESOURCES;
    }

    /**
     * Returns all registered resources.
     *
     * @return a {@link Map} containing all registered resources identified by their name
     *
     * @throws IOException if the properties file cannot be found
     */
    private static Map<String, String> getRegisteredResources() throws IOException {
        if (isNull(REGISTERED_RESOURCES)) {
            Properties properties = new Properties();
            properties.load(ResourceHelper.class.getResourceAsStream("/resources.properties"));
            REGISTERED_RESOURCES = properties.entrySet().stream()
                    .collect(Collectors.toMap(e -> e.getKey().toString(), e -> e.getValue().toString()));
        }
        return REGISTERED_RESOURCES;
    }

    /**
     * Extracts a {@link ZipEntry}, named {@code filename}, from the default ZIP file to the {@code outputDir}.
     *
     * @param filename  the file name of the {@link ZipEntry} to extract
     * @param outputDir the directory where to extract the file
     *
     * @return the extracted file
     *
     * @throws IOException if an I/O error occurs during the extraction
     */
    private static File extractFromZip(String filename, Path outputDir) throws IOException {
        File outputFile = null;
        boolean fileFound = false;
        try (ZipInputStream inputStream = new ZipInputStream(ResourceHelper.class.getResourceAsStream("/" + ZIP_FILENAME))) {
            ZipEntry entry = inputStream.getNextEntry();
            while (nonNull(entry) || !fileFound) {
                if (!entry.isDirectory() && Objects.equals(new File(entry.getName()).getName(), filename)) {
                    outputFile = extractEntryFromZip(inputStream, entry, outputDir);
                    fileFound = true;
                }
                inputStream.closeEntry();
                entry = inputStream.getNextEntry();
            }
        }
        return outputFile;
    }

    /**
     * Extracts a {@link ZipEntry} from the given {@code input} to the {@code outputDir}.
     *
     * @param input     the input stream of the ZIP file
     * @param entry     the entry in the ZIP file
     * @param outputDir the directory where to extract the file
     *
     * @return the extracted file
     *
     * @throws IOException if an I/O error occurs during the extraction
     */
    private static File extractEntryFromZip(ZipInputStream input, ZipEntry entry, Path outputDir) throws IOException {
        File outputFile = outputDir.resolve(new File(entry.getName()).getName()).toFile();
        if (outputFile.exists()) {
            Log.info("Already extracted resource {0}", outputFile);
            return outputFile;
        }

        try (OutputStream output = new FileOutputStream(outputFile)) {
            final byte[] buffer = new byte[4096];
            int count;
            while (-1 != (count = input.read(buffer))) {
                output.write(buffer, 0, count);
            }
        }

        return outputFile;
    }

    /**
     * Retrieves the file name without its extension of {@code file}.
     *
     * @param file the file name
     *
     * @return the filename without its extension
     */
    public static String getNameWithoutExtension(String file) {
        checkNotNull(file);

        String fileName = new File(file).getName();
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex == -1) ? fileName : fileName.substring(0, dotIndex);
    }
}