/*
 * Copyright 2005 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.drools.modelcompiler;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.drools.compiler.addon.DependencyFilter;
import org.drools.compiler.addon.PomModel;
import org.drools.compiler.builder.impl.KnowledgeBuilderImpl;
import org.drools.compiler.compiler.ProcessBuilder;
import org.drools.compiler.kie.builder.impl.AbstractKieModule;
import org.drools.compiler.kie.builder.impl.FileKieModule;
import org.drools.compiler.kie.builder.impl.InternalKieModule;
import org.drools.compiler.kie.builder.impl.KieBaseUpdateContext;
import org.drools.compiler.kie.builder.impl.KieProject;
import org.drools.compiler.kie.builder.impl.KnowledgePackagesBuildResult;
import org.drools.compiler.kie.builder.impl.ResultsImpl;
import org.drools.compiler.kie.builder.impl.ZipKieModule;
import org.drools.compiler.kie.util.KieJarChangeSet;
import org.drools.compiler.kproject.models.KieBaseModelImpl;
import org.drools.compiler.kproject.models.KieModuleModelImpl;
import org.drools.core.RuleBaseConfiguration;
import org.drools.core.definitions.InternalKnowledgePackage;
import org.drools.core.definitions.impl.KnowledgePackageImpl;
import org.drools.core.impl.InternalKnowledgeBase;
import org.drools.core.util.Drools;
import org.drools.core.util.IoUtils;
import org.drools.core.util.StringUtils;
import org.drools.model.Model;
import org.drools.model.NamedModelItem;
import org.drools.modelcompiler.builder.CanonicalKieBaseUpdater;
import org.drools.modelcompiler.builder.CanonicalModelKieProject;
import org.drools.modelcompiler.builder.KieBaseBuilder;
import org.drools.reflective.ResourceProvider;
import org.drools.reflective.classloader.ProjectClassLoader;
import org.kie.api.KieBaseConfiguration;
import org.kie.api.KieServices;
import org.kie.api.builder.Message;
import org.kie.api.builder.ReleaseId;
import org.kie.api.builder.Results;
import org.kie.api.builder.model.KieBaseModel;
import org.kie.api.builder.model.KieModuleModel;
import org.kie.api.definition.KiePackage;
import org.kie.api.definition.process.Process;
import org.kie.api.io.Resource;
import org.kie.api.io.ResourceConfiguration;
import org.kie.api.io.ResourceType;
import org.kie.internal.builder.ChangeType;
import org.kie.internal.builder.CompositeKnowledgeBuilder;
import org.kie.internal.builder.KnowledgeBuilder;
import org.kie.internal.builder.KnowledgeBuilderConfiguration;
import org.kie.internal.builder.KnowledgeBuilderFactory;
import org.kie.internal.builder.ResourceChange;
import org.kie.internal.builder.ResourceChangeSet;

import static java.util.stream.Collectors.toList;

import static org.drools.compiler.kie.builder.impl.AbstractKieModule.checkStreamMode;
import static org.drools.model.impl.ModelComponent.areEqualInModel;
import static org.drools.modelcompiler.builder.CanonicalModelKieProject.PROJECT_MODEL_RESOURCE_CLASS;
import static org.drools.modelcompiler.util.StringUtil.fileNameToClass;
import static org.kie.api.io.ResourceType.determineResourceType;

public class CanonicalKieModule implements InternalKieModule {

    public static final String MODEL_FILE = "META-INF/kie/drools-model";

    public static final String MODEL_VERSION = "Drools-Model-Version:";

    private final InternalKieModule internalKieModule;

    private Collection<String> ruleClassesNames;

    private final Map<String, CanonicalKiePackages> pkgsInKbase = new HashMap<>();

    private final Map<String, Model> models = new HashMap<>();

    private ProjectClassLoader moduleClassLoader;

    private boolean incrementalUpdate = false;

    private KieModuleModel kieModuleModel;

    public CanonicalKieModule( ReleaseId releaseId, KieModuleModel kieProject, File file ) {
        this( releaseId, kieProject, file, null );
    }

    public CanonicalKieModule( ReleaseId releaseId, KieModuleModel kieProject, File file, Collection<String> ruleClassesNames ) {
        this( file.isDirectory() ? new FileKieModule( releaseId, kieProject, file ) : new ZipKieModule( releaseId, kieProject, file ), ruleClassesNames );
    }

    public CanonicalKieModule( InternalKieModule internalKieModule ) {
        this( internalKieModule, null );
    }

    private CanonicalKieModule( InternalKieModule internalKieModule, Collection<String> ruleClassesNames ) {
        this.internalKieModule = internalKieModule;
        this.ruleClassesNames = ruleClassesNames;
    }

    public static CanonicalKieModule createFromClasspath() {
        return createFromClassLoader(null);
    }

    public static CanonicalKieModule createFromClassLoader(ClassLoader classLoader) {
        CanonicalKieModuleModel kmodel = getModuleModel( classLoader );
        return kmodel == null ? null : new CanonicalKieModule( new CanonicalInternalKieModule(kmodel.getReleaseId(), kmodel.getKieModuleModel()) );
    }

    public static CanonicalKieModule createFromClassLoader(ClassLoader classLoader, InternalKieModule kieModule) {
        CanonicalKieModule canonicalKieModule = createFromClassLoader(classLoader);
        if (canonicalKieModule == null) {
            canonicalKieModule = new CanonicalKieModule( kieModule );
        }
        return canonicalKieModule;
    }

    private static CanonicalKieModuleModel getModuleModel(ClassLoader classLoader) {
        return createInstance( classLoader, CanonicalModelKieProject.PROJECT_MODEL_CLASS );
    }

    @Override
    public Map<String, byte[]> getClassesMap() {
        return internalKieModule.getClassesMap();
    }

    @Override
    public ResultsImpl build() {
        // TODO should this initialize the CanonicalKieModule in some way? (doesn't seem necessary so far)
        return new ResultsImpl();
    }

    @Override
    public InternalKnowledgeBase createKieBase( KieBaseModelImpl kBaseModel, KieProject kieProject, ResultsImpl messages, KieBaseConfiguration conf ) {
        this.moduleClassLoader = (( ProjectClassLoader ) kieProject.getClassLoader());
        KieBaseConfiguration kBaseConf = getKieBaseConfiguration( kBaseModel, moduleClassLoader, conf );

        KnowledgePackagesBuildResult knowledgePackagesBuildResult = ((AbstractKieModule)internalKieModule).buildKnowledgePackages(kBaseModel, kieProject, messages);
        if(knowledgePackagesBuildResult.hasErrors()) {
            return null;
        }

        CanonicalKiePackages kpkgs = pkgsInKbase.computeIfAbsent( kBaseModel.getName(), k -> createKiePackages(kieProject, kBaseModel, messages, kBaseConf) );
        checkStreamMode( kBaseModel, conf, kpkgs.getKiePackages() );
        InternalKnowledgeBase kieBase = new KieBaseBuilder(kBaseModel, kBaseConf).createKieBase(kpkgs);

        Collection<KiePackage> pkgs = knowledgePackagesBuildResult.getPkgs();
        for(KiePackage pk : pkgs) {
            if(kieBase.getPackage(pk.getName()) == null) {
                kieBase.addPackages(pkgs);
            }
        }

        return kieBase;
    }

    private CanonicalKiePackages createKiePackages( KieProject kieProject, KieBaseModelImpl kBaseModel, ResultsImpl messages, KieBaseConfiguration conf ) {
        Set<String> includes = kieProject == null ? Collections.emptySet() : kieProject.getTransitiveIncludes(kBaseModel);
        List<Process> processes = findProcesses( internalKieModule, kBaseModel );
        Collection<Model> models;

        if (includes.isEmpty()) {
            models = getModelForKBase(kBaseModel);

        } else {
            models = new ArrayList<>( getModelForKBase(kBaseModel) );

            for (String include : includes) {
                if ( StringUtils.isEmpty( include ) ) {
                    continue;
                }
                InternalKieModule includeModule = kieProject.getKieModuleForKBase( include );
                if ( includeModule == null ) {
                    String text = "Unable to build KieBase, could not find include: " + include;
                    messages.addMessage( Message.Level.ERROR, KieModuleModelImpl.KMODULE_SRC_PATH, text ).setKieBaseName( kBaseModel.getName() );
                    continue;
                }
                if ( !(includeModule instanceof CanonicalKieModule) ) {
                    String text = "It is not possible to mix drl based and executable model based projects. Found a drl project: " + include;
                    messages.addMessage( Message.Level.ERROR, KieModuleModelImpl.KMODULE_SRC_PATH, text ).setKieBaseName( kBaseModel.getName() );
                    continue;
                }
                KieBaseModelImpl includeKBaseModel = ( KieBaseModelImpl ) kieProject.getKieBaseModel( include );
                models.addAll( (( CanonicalKieModule ) includeModule).getModelForKBase( includeKBaseModel ) );
                processes.addAll( findProcesses( includeModule, includeKBaseModel ) );
            }
        }

        CanonicalKiePackages canonicalKiePkgs = new KiePackagesBuilder(conf, models).build();
        return mergeProcesses( processes, canonicalKiePkgs );
    }

    private CanonicalKiePackages mergeProcesses( List<Process> processes, CanonicalKiePackages canonicalKiePkgs ) {
        for (Process process : processes) {
            InternalKnowledgePackage canonicalKiePkg = ( InternalKnowledgePackage ) canonicalKiePkgs.getKiePackage( process.getPackageName() );
            if ( canonicalKiePkg == null ) {
                canonicalKiePkg = new KnowledgePackageImpl( process.getPackageName() );
                canonicalKiePkgs.addKiePackage( canonicalKiePkg );
            }
            canonicalKiePkg.addProcess( process );
        }
        return canonicalKiePkgs;
    }

    private List<Process> findProcesses( InternalKieModule kieModule, KieBaseModelImpl kBaseModel ) {
        List<Process> processes = new ArrayList<>();
        Collection<KiePackage> pkgs = kieModule.getKnowledgePackagesForKieBase(kBaseModel.getName());
        if (pkgs == null) {
            List<Resource> processResources = kieModule.getFileNames().stream()
                    .filter( fileName -> {
                        ResourceType resourceType = determineResourceType(fileName);
                        return resourceType == ResourceType.DRF || resourceType == ResourceType.BPMN2;
                    } )
                    .map( kieModule::getResource )
                    .collect( toList() );
            if (!processResources.isEmpty()) {
                KnowledgeBuilderImpl kbuilder = (KnowledgeBuilderImpl) KnowledgeBuilderFactory.newKnowledgeBuilder( getBuilderConfiguration( kBaseModel, moduleClassLoader ) );
                ProcessBuilder processBuilder = kbuilder.getProcessBuilder();
                if (processBuilder != null) {
                    for (Resource processResource : processResources) {
                        try {
                            processes.addAll( processBuilder.addProcessFromXml( processResource ) );
                        } catch (IOException e) {
                            throw new RuntimeException( e );
                        }
                    }
                }
            }
        } else {
            for (KiePackage pkg : pkgs) {
                processes.addAll( pkg.getProcesses() );
            }
        }
        return processes;
    }

    public CanonicalKiePackages getKiePackages( KieBaseModelImpl kBaseModel ) {
        return pkgsInKbase.computeIfAbsent( kBaseModel.getName(), k -> createKiePackages(null, kBaseModel, null, getKnowledgeBaseConfiguration(kBaseModel, getModuleClassLoader())) );
    }

    public ProjectClassLoader getModuleClassLoader() {
        if (moduleClassLoader == null) {
            moduleClassLoader = createModuleClassLoader( null );
            moduleClassLoader.storeClasses( getClassesMap() );
        }
        return moduleClassLoader;
    }

    public void setModuleClassLoader(ProjectClassLoader moduleClassLoader) {
        pkgsInKbase.clear();
        models.clear();
        this.moduleClassLoader = moduleClassLoader;
    }

    public void setIncrementalUpdate( boolean incrementalUpdate ) {
        this.incrementalUpdate = incrementalUpdate;
    }

    private Map<String, Model> getModels() {
        if ( models.isEmpty() ) {
            // During incremental update, to keep compatible classes generated from declared types, the new kmodule
            // is loaded with the classloader of the old one. This implies that the models cannot be retrieved from the
            // project model class but loaded one by one from the classloader itself.
            if (incrementalUpdate) {
                initModelsFromProjectDescriptor();
            } else {
                initModels( getModuleModel( getModuleClassLoader() ) );
            }
        }
        return models;
    }

    private void initModelsFromProjectDescriptor() {
        for (String rulesFile : getRuleClassNames()) {
            Model model = createInstance( getModuleClassLoader(), rulesFile );
            models.put( model.getName(), model );
        }
    }

    private void initModels( CanonicalKieModuleModel kmodel ) {
        if (kmodel != null) {
            ruleClassesNames = new ArrayList<>();
            for (Model model : kmodel.getModels()) {
                models.put( model.getName(), model );
                ruleClassesNames.add( model.getClass().getCanonicalName() );
            }
        } else {
            initModelsFromProjectDescriptor();
        }
    }

    private Collection<String> getRuleClassNames() {
        if ( ruleClassesNames == null ) {
            ruleClassesNames = findRuleClassesNames( getModuleClassLoader() );
        }
        return ruleClassesNames;
    }

    private Collection<Model> getModelForKBase(KieBaseModelImpl kBaseModel) {
        Map<String, Model> modelsMap = getModels();
        if (kBaseModel.getPackages().isEmpty()) {
            return modelsMap.values();
        }
        Collection<Model> models = new ArrayList<>();
        for (String pkg : kBaseModel.getPackages()) {
            if (pkg.equals( "*" )) {
                return modelsMap.values();
            }
            Model model = modelsMap.get(pkg);
            if ( model != null ) {
                models.add( model );
            }
        }
        return models;
    }

    private static Collection<String> findRuleClassesNames( ClassLoader kieProjectCL) {
        String modelFiles;
        try {
            modelFiles = new String( IoUtils.readBytesFromInputStream( kieProjectCL.getResourceAsStream( MODEL_FILE ) ) );
        } catch (IOException e) {
            throw new RuntimeException( e );
        }

        String[] lines = modelFiles.split( "\n" );
        String header = lines[0];
        if ( !header.startsWith( MODEL_VERSION ) ) {
            throw new RuntimeException( "Malformed drools-model file" );
        }
        String version = header.substring( MODEL_VERSION.length() );
        if ( !areModelVersionsCompatible( Drools.getFullVersion(), version ) ) {
            throw new RuntimeException( "Kjar compiled with version " + version + " is not compatible with current runtime version " + Drools.getFullVersion() );
        }

        return Stream.of( lines ).skip( 1 ).collect( toList() );
    }

    private static boolean areModelVersionsCompatible( String runtimeVersion, String compileVersion ) {
        return true;
    }

    private static <T> T createInstance( ClassLoader cl, String className ) {
        try {
            return ( T ) (cl == null ? Class.forName( className ) : cl.loadClass( className )).newInstance();
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            return null;
        }
    }

    @Override
    public KieJarChangeSet getChanges( InternalKieModule newKieModule ) {
        KieJarChangeSet result = findChanges(newKieModule);

        Map<String, Model> oldModels = getModels();
        Map<String, Model> newModels = (( CanonicalKieModule ) newKieModule).getModels();

        for (Map.Entry<String, Model> entry : oldModels.entrySet()) {
            Model newModel = newModels.get( entry.getKey() );
            if ( newModel == null ) {
                result.registerChanges( entry.getKey(), buildAllItemsChangeSet( entry.getValue(), ChangeType.REMOVED ) );
                continue;
            }

            Model oldModel = entry.getValue();
            for (ResourceChangeSet changeSet : calculateResourceChangeSet( oldModel, newModel )) {
                if ( !changeSet.getChanges().isEmpty() ) {
                    result.registerChanges( entry.getKey(), changeSet );
                }
            }
        }

        for (Map.Entry<String, Model> entry : newModels.entrySet()) {
            if ( oldModels.get( entry.getKey() ) == null ) {
                result.registerChanges( entry.getKey(), buildAllItemsChangeSet( entry.getValue(), ChangeType.ADDED ) );
            }
        }

        KieJarChangeSet internalChanges = internalKieModule.getChanges(((CanonicalKieModule) newKieModule).internalKieModule);
        internalChanges.removeFile( PROJECT_MODEL_RESOURCE_CLASS );
        return result.merge(internalChanges);
    }

    private ResourceChangeSet buildAllItemsChangeSet( Model oldModel, ChangeType changeType ) {
        ResourceChangeSet changeSet = new ResourceChangeSet( oldModel.getName(), ChangeType.UPDATED );
        for (NamedModelItem item : oldModel.getRules()) {
            changeSet.getChanges().add( new ResourceChange( changeType, ResourceChange.Type.RULE, item.getName() ) );
        }
        for (NamedModelItem item : oldModel.getQueries()) {
            changeSet.getChanges().add( new ResourceChange( changeType, ResourceChange.Type.RULE, item.getName() ) );
        }
        for (NamedModelItem item : oldModel.getGlobals()) {
            changeSet.getChanges().add( new ResourceChange( changeType, ResourceChange.Type.GLOBAL, item.getName() ) );
        }
        for (NamedModelItem item : oldModel.getTypeMetaDatas()) {
            changeSet.getChanges().add( new ResourceChange( changeType, ResourceChange.Type.DECLARATION, item.getName() ) );
        }
        return changeSet;
    }

    private KieJarChangeSet findChanges(InternalKieModule newKieModule) {
        KieJarChangeSet result = new KieJarChangeSet();
        Collection<String> oldFiles = getFileNames();
        Collection<String> newFiles = newKieModule.getFileNames();

        ArrayList<String> removedFiles = new ArrayList<>( oldFiles );
        removedFiles.removeAll( newFiles );
        if( ! removedFiles.isEmpty() ) {
            for( String file : removedFiles ) {
                if ( isChange( file, this ) ) {
                    result.removeFile( file );
                }
            }
        }

        for ( String file : newFiles ) {
            if ( oldFiles.contains( file ) && isChange( file, this ) ) {
                // check for modification
                byte[] oldBytes = getBytes( file );
                byte[] newBytes = newKieModule.getBytes( file );
                if( ! Arrays.equals( oldBytes, newBytes ) ) {
                    // parse the file to figure out the difference
                    result.registerChanges( file, new ResourceChangeSet( file, ChangeType.UPDATED ) );
                }
            } else if (isChange( file, (( CanonicalKieModule ) newKieModule) )) {
                // file was added
                result.addFile( file );
            }
        }

        return result;
    }

    private boolean isChange(String fileName, CanonicalKieModule module) {
        return fileName.endsWith( ".class" ) &&
                !fileName.equals( PROJECT_MODEL_RESOURCE_CLASS ) &&
                module.getRuleClassNames().stream().noneMatch( fileNameToClass(fileName)::startsWith );
    }

    private Collection<ResourceChangeSet> calculateResourceChangeSet( Model oldModel, Model newModel ) {
        ResourceChangeSet changeSet = new ResourceChangeSet( oldModel.getName(), ChangeType.UPDATED );
        Map<String, ResourceChangeSet> changes = new HashMap<>();
        changes.put( oldModel.getName(), changeSet );

        addModifiedItemsToChangeSet( changeSet, ResourceChange.Type.RULE, oldModel.getRules(), newModel.getRules() );
        addModifiedItemsToChangeSet( changeSet, ResourceChange.Type.RULE, oldModel.getQueries(), newModel.getQueries() );
        addModifiedItemsToChangeSet( changeSet, ResourceChange.Type.GLOBAL, oldModel.getGlobals(), newModel.getGlobals() );
        addModifiedItemsToChangeSet( changeSet, changes, ResourceChange.Type.DECLARATION, oldModel.getTypeMetaDatas(), newModel.getTypeMetaDatas() );

        return changes.values();
    }

    private void addModifiedItemsToChangeSet( ResourceChangeSet changeSet, ResourceChange.Type type, List<? extends NamedModelItem> oldItems, List<? extends NamedModelItem> newItems ) {
        addModifiedItemsToChangeSet( changeSet, null, type, oldItems, newItems );
    }

    private void addModifiedItemsToChangeSet( ResourceChangeSet mainChangeSet, Map<String, ResourceChangeSet> changes, ResourceChange.Type type, List<? extends NamedModelItem> oldItems, List<? extends NamedModelItem> newItems ) {
        if ( oldItems.isEmpty() ) {
            if ( !newItems.isEmpty() ) {
                for (NamedModelItem newItem : newItems) {
                    registerChange( mainChangeSet, changes, type, ChangeType.ADDED, newItem );
                }
            }
            return;
        } else if ( newItems.isEmpty() ) {
            for (NamedModelItem oldItem : oldItems) {
                registerChange( mainChangeSet, changes, type, ChangeType.REMOVED, oldItem );
            }
            return;
        }

        oldItems.sort( Comparator.comparing( NamedModelItem::getName ) );
        newItems.sort( Comparator.comparing( NamedModelItem::getName ) );

        Iterator<? extends NamedModelItem> oldRulesIterator = oldItems.iterator();
        Iterator<? extends NamedModelItem> newRulesIterator = newItems.iterator();

        NamedModelItem currentOld = oldRulesIterator.next();
        NamedModelItem currentNew = newRulesIterator.next();

        while (true) {
            int compare = currentOld.getName().compareTo( currentNew.getName() );
            if ( compare == 0 ) {
                if ( !areEqualInModel( currentOld, currentNew ) ) {
                    registerChange( mainChangeSet, changes, type, ChangeType.UPDATED, currentOld );
                }
                if ( oldRulesIterator.hasNext() ) {
                    currentOld = oldRulesIterator.next();
                } else {
                    break;
                }
                if ( newRulesIterator.hasNext() ) {
                    currentNew = newRulesIterator.next();
                } else {
                    registerChange( mainChangeSet, changes, type, ChangeType.REMOVED, currentOld );
                    break;
                }
            } else if ( compare < 0 ) {
                registerChange( mainChangeSet, changes, type, ChangeType.REMOVED, currentOld );
                if ( oldRulesIterator.hasNext() ) {
                    currentOld = oldRulesIterator.next();
                } else {
                    registerChange( mainChangeSet, changes, type, ChangeType.ADDED, currentNew );
                    break;
                }
            } else {
                registerChange( mainChangeSet, changes, type, ChangeType.ADDED, currentNew );
                if ( newRulesIterator.hasNext() ) {
                    currentNew = newRulesIterator.next();
                } else {
                    registerChange( mainChangeSet, changes, type, ChangeType.REMOVED, currentOld );
                    break;
                }
            }
        }

        while (oldRulesIterator.hasNext()) {
            registerChange( mainChangeSet, changes, type, ChangeType.REMOVED, oldRulesIterator.next() );
        }

        while (newRulesIterator.hasNext()) {
            registerChange( mainChangeSet, changes, type, ChangeType.ADDED, newRulesIterator.next() );
        }
    }

    private void registerChange( ResourceChangeSet mainChangeSet, Map<String, ResourceChangeSet> changes, ResourceChange.Type resourceChangeType, ChangeType changeType, NamedModelItem item ) {
        getChangeSetForItem(mainChangeSet, changes, item ).getChanges().add( new ResourceChange( changeType, resourceChangeType, item.getName() ) );
    }

    private ResourceChangeSet getChangeSetForItem(ResourceChangeSet mainChangeSet, Map<String, ResourceChangeSet> changes, NamedModelItem item) {
        return changes != null ? changes.computeIfAbsent( item.getPackage(), pkg -> new ResourceChangeSet( pkg, ChangeType.UPDATED ) ) : mainChangeSet;
    }

    @Override
    public Runnable createKieBaseUpdater(KieBaseUpdateContext context) {
        return new CanonicalKieBaseUpdater( context );
    }

    private static KieBaseConfiguration getKieBaseConfiguration( KieBaseModelImpl kBaseModel, ClassLoader cl, KieBaseConfiguration conf ) {
        if (conf == null) {
            conf = getKnowledgeBaseConfiguration(kBaseModel, cl);
        } else if (conf instanceof RuleBaseConfiguration ) {
            ((RuleBaseConfiguration)conf).setClassLoader(cl);
        }
        return conf;
    }

    private static KieBaseConfiguration getKnowledgeBaseConfiguration( KieBaseModelImpl kBaseModel, ClassLoader cl ) {
        KieBaseConfiguration kbConf = KieServices.get().newKieBaseConfiguration( null, cl );
        if (kBaseModel != null) {
            kbConf.setOption( kBaseModel.getEqualsBehavior() );
            kbConf.setOption( kBaseModel.getEventProcessingMode() );
            kbConf.setOption( kBaseModel.getDeclarativeAgenda() );
        }
        return kbConf;
    }

    public InternalKieModule getInternalKieModule() {
        return internalKieModule;
    }

    @Override
    public void initModel() {
        initModel( moduleClassLoader );
    }

    @Override
    public void initModel(ClassLoader classLoader) {
        CanonicalKieModuleModel kmodel = getModuleModel( classLoader );
        if (kmodel != null) {
            initModels( kmodel );
            kieModuleModel = kmodel.getKieModuleModel();
        } else {
            kieModuleModel = internalKieModule.getKieModuleModel();
        }
    }

    @Override
    public KieModuleModel getKieModuleModel() {
        return kieModuleModel;
    }

    // Delegate methods

    @Override
    public void cacheKnowledgeBuilderForKieBase( String kieBaseName, KnowledgeBuilder kbuilder ) {
        internalKieModule.cacheKnowledgeBuilderForKieBase( kieBaseName, kbuilder );
    }

    @Override
    public KnowledgeBuilder getKnowledgeBuilderForKieBase( String kieBaseName ) {
        return internalKieModule.getKnowledgeBuilderForKieBase( kieBaseName );
    }

    @Override
    public Collection<KiePackage> getKnowledgePackagesForKieBase( String kieBaseName ) {
        return internalKieModule.getKnowledgePackagesForKieBase( kieBaseName );
    }

    @Override
    public void cacheResultsForKieBase( String kieBaseName, Results results ) {
        internalKieModule.cacheResultsForKieBase( kieBaseName, results );
    }

    @Override
    public Map<String, Results> getKnowledgeResultsCache() {
        return internalKieModule.getKnowledgeResultsCache();
    }

    @Override
    public byte[] getBytes() {
        return internalKieModule.getBytes();
    }

    @Override
    public boolean hasResource( String fileName ) {
        return internalKieModule.hasResource( fileName );
    }

    @Override
    public Resource getResource( String fileName ) {
        return internalKieModule.getResource( fileName );
    }

    @Override
    public ResourceConfiguration getResourceConfiguration( String fileName ) {
        return internalKieModule.getResourceConfiguration( fileName );
    }

    @Override
    public Map<ReleaseId, InternalKieModule> getKieDependencies() {
        return internalKieModule.getKieDependencies();
    }

    @Override
    public void addKieDependency( InternalKieModule dependency ) {
        internalKieModule.addKieDependency( dependency );
    }

    @Override
    public Collection<ReleaseId> getJarDependencies( DependencyFilter filter ) {
        return internalKieModule.getJarDependencies( filter );
    }

    @Override
    public Collection<ReleaseId> getUnresolvedDependencies() {
        return internalKieModule.getUnresolvedDependencies();
    }

    @Override
    public void setUnresolvedDependencies( Collection<ReleaseId> unresolvedDependencies ) {
        internalKieModule.setUnresolvedDependencies( unresolvedDependencies );
    }

    @Override
    public boolean isAvailable( String pResourceName ) {
        return internalKieModule.isAvailable( pResourceName );
    }

    @Override
    public byte[] getBytes( String pResourceName ) {
        return internalKieModule.getBytes( pResourceName );
    }

    @Override
    public Collection<String> getFileNames() {
        return internalKieModule.getFileNames();
    }

    @Override
    public File getFile() {
        return internalKieModule.getFile();
    }

    @Override
    public ResourceProvider createResourceProvider() {
        return internalKieModule.createResourceProvider();
    }

    @Override
    public boolean addResourceToCompiler( CompositeKnowledgeBuilder ckbuilder, KieBaseModel kieBaseModel, String fileName ) {
        return internalKieModule.addResourceToCompiler( ckbuilder, kieBaseModel, fileName );
    }

    @Override
    public boolean addResourceToCompiler( CompositeKnowledgeBuilder ckbuilder, KieBaseModel kieBaseModel, String fileName, ResourceChangeSet rcs ) {
        return internalKieModule.addResourceToCompiler( ckbuilder, kieBaseModel, fileName, rcs );
    }

    @Override
    public long getCreationTimestamp() {
        return internalKieModule.getCreationTimestamp();
    }

    @Override
    public InputStream getPomAsStream() {
        return internalKieModule.getPomAsStream();
    }

    @Override
    public PomModel getPomModel() {
        return internalKieModule.getPomModel();
    }

    @Override
    public KnowledgeBuilderConfiguration getBuilderConfiguration( KieBaseModel kBaseModel, ClassLoader classLoader ) {
        return internalKieModule.getBuilderConfiguration( kBaseModel, classLoader );
    }

    @Override
    public ReleaseId getReleaseId() {
        return internalKieModule.getReleaseId();
    }
}