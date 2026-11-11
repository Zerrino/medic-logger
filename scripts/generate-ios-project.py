#!/usr/bin/env python3
"""Create the dependency-free Xcode project with stable IDs. Run from the repo root."""
from pathlib import Path
import hashlib, json

ROOT = Path(__file__).resolve().parent.parent / 'ios'
objects = {}
def uid(key): return hashlib.sha1(key.encode()).hexdigest()[:24].upper()
def add(key, **values):
    identifier = uid(key); objects[identifier] = values; return identifier
def ref(path, filetype=None):
    if filetype is None:
        filetype = 'sourcecode.swift' if path.endswith('.swift') else 'folder.assetcatalog' if path.endswith('.xcassets') else 'text.xml' if path.endswith('.xcprivacy') else 'file'
    return add('file:' + path, isa='PBXFileReference', lastKnownFileType=filetype, path=path, sourceTree='<group>')
def config_list(key, settings):
    configs=[]
    for name in ['Debug','Release']:
        config=dict(settings)
        config.update(SWIFT_OPTIMIZATION_LEVEL='-Onone' if name=='Debug' else '-O',DEBUG_INFORMATION_FORMAT='dwarf' if name=='Debug' else 'dwarf-with-dsym')
        if name=='Debug': config.update(SWIFT_ACTIVE_COMPILATION_CONDITIONS='DEBUG $(inherited)',ONLY_ACTIVE_ARCH='YES')
        else: config['SWIFT_COMPILATION_MODE']='wholemodule'
        configs.append(add(key+':'+name,isa='XCBuildConfiguration',name=name,buildSettings=config))
    return add(key+':configs',isa='XCConfigurationList',buildConfigurations=configs,defaultConfigurationIsVisible=0,defaultConfigurationName='Release')

project_id=uid('project'); target_ids={n:uid('target:'+n) for n in ['MedicLogger','MedicLoggerStatus','MedicLoggerTests','MedicLoggerUITests']}
product_ids={}
for name in target_ids:
    ext='app' if name=='MedicLogger' else 'appex' if name=='MedicLoggerStatus' else 'xctest'
    product_ids[name]=add('product:'+name,isa='PBXFileReference',explicitFileType='wrapper.application' if ext=='app' else 'wrapper.app-extension' if ext=='appex' else 'wrapper.cfbundle',includeInIndex=0,path=name+'.'+ext,sourceTree='BUILT_PRODUCTS_DIR')

groups=[]; refs={}
for folder in ['MedicLogger','Shared','MedicLoggerWidgets','MedicLoggerTests','MedicLoggerUITests','Config']:
    files=[]
    for path in sorted((ROOT/folder).iterdir()):
        if path.is_file() or path.suffix=='.xcassets':
            relative=str(path.relative_to(ROOT));refs[relative]=ref(relative);files.append(refs[relative])
    groups.append(add('group:'+folder,isa='PBXGroup',children=files,name=folder,sourceTree='<group>'))
products=add('products',isa='PBXGroup',name='Products',children=list(product_ids.values()),sourceTree='<group>')
main=add('main',isa='PBXGroup',children=groups+[products],sourceTree='<group>')

common={'IPHONEOS_DEPLOYMENT_TARGET':'17.0','SWIFT_VERSION':'5.0','SDKROOT':'iphoneos','SUPPORTED_PLATFORMS':'iphoneos iphonesimulator','TARGETED_DEVICE_FAMILY':'1,2','CODE_SIGN_STYLE':'Automatic','CURRENT_PROJECT_VERSION':'8','MARKETING_VERSION':'1.4.2','CLANG_ENABLE_MODULES':'YES','ENABLE_USER_SCRIPT_SANDBOXING':'YES','SWIFT_STRICT_CONCURRENCY':'minimal'}
for name in target_ids:
    folder={'MedicLogger':'MedicLogger','MedicLoggerStatus':'MedicLoggerWidgets','MedicLoggerTests':'MedicLoggerTests','MedicLoggerUITests':'MedicLoggerUITests'}[name]
    paths=[p for p in refs if p.startswith(folder+'/')]
    if name in ['MedicLogger','MedicLoggerStatus']: paths += [p for p in refs if p.startswith('Shared/')]
    sources=[];resources=[]
    for path in paths:
        bf=add('build:'+name+':'+path,isa='PBXBuildFile',fileRef=refs[path])
        (sources if path.endswith('.swift') else resources).append(bf)
    source_phase=add('sources:'+name,isa='PBXSourcesBuildPhase',buildActionMask=2147483647,files=sources,runOnlyForDeploymentPostprocessing=0)
    res_phase=add('resources:'+name,isa='PBXResourcesBuildPhase',buildActionMask=2147483647,files=resources,runOnlyForDeploymentPostprocessing=0)
    phases=[source_phase,res_phase];dependencies=[]
    dep='MedicLoggerStatus' if name=='MedicLogger' else 'MedicLogger' if name.endswith('Tests') else None
    if dep:
        proxy=add('proxy:'+name,isa='PBXContainerItemProxy',containerPortal=project_id,proxyType=1,remoteGlobalIDString=target_ids[dep],remoteInfo=dep)
        dependencies.append(add('dep:'+name,isa='PBXTargetDependency',target=target_ids[dep],targetProxy=proxy))
    if name=='MedicLogger':
        bf=add('embed:widget',isa='PBXBuildFile',fileRef=product_ids['MedicLoggerStatus'],settings={'ATTRIBUTES':['RemoveHeadersOnCopy']})
        phases.append(add('embed',isa='PBXCopyFilesBuildPhase',buildActionMask=2147483647,dstPath='',dstSubfolderSpec=13,files=[bf],name='Embed App Extensions',runOnlyForDeploymentPostprocessing=0))
    settings=dict(common);settings.update(PRODUCT_NAME='$(TARGET_NAME)',PRODUCT_BUNDLE_IDENTIFIER={'MedicLogger':'fr.mediclogger.app','MedicLoggerStatus':'fr.mediclogger.app.status','MedicLoggerTests':'fr.mediclogger.tests','MedicLoggerUITests':'fr.mediclogger.uitests'}[name])
    if name in ['MedicLogger','MedicLoggerStatus']:
        settings.update(GENERATE_INFOPLIST_FILE='NO',INFOPLIST_FILE='Config/'+('App' if name=='MedicLogger' else 'Widget')+'-Info.plist',LD_RUNPATH_SEARCH_PATHS='$(inherited) @executable_path/Frameworks' + (' @executable_path/../../Frameworks' if name=='MedicLoggerStatus' else ''))
        if name=='MedicLogger': settings.update(ASSETCATALOG_COMPILER_APPICON_NAME='AppIcon',ASSETCATALOG_COMPILER_GLOBAL_ACCENT_COLOR_NAME='AccentColor',ENABLE_PREVIEWS='YES')
        else: settings.update(APPLICATION_EXTENSION_API_ONLY='YES',SKIP_INSTALL='YES')
    else:
        settings.update(GENERATE_INFOPLIST_FILE='YES',ENABLE_TESTABILITY='YES')
        if name=='MedicLoggerTests': settings.update(TEST_HOST='$(BUILT_PRODUCTS_DIR)/MedicLogger.app/$(BUNDLE_EXECUTABLE_FOLDER_PATH)/MedicLogger',BUNDLE_LOADER='$(TEST_HOST)')
        else: settings.update(TEST_TARGET_NAME='MedicLogger')
    objects[target_ids[name]]=dict(isa='PBXNativeTarget',buildConfigurationList=config_list('config:'+name,settings),buildPhases=phases,buildRules=[],dependencies=dependencies,name=name,productName=name,productReference=product_ids[name],productType='com.apple.product-type.application' if name=='MedicLogger' else 'com.apple.product-type.app-extension' if name=='MedicLoggerStatus' else 'com.apple.product-type.bundle.unit-test' if name=='MedicLoggerTests' else 'com.apple.product-type.bundle.ui-testing')

objects[project_id]=dict(isa='PBXProject',attributes={'BuildIndependentTargetsInParallel':'YES','LastSwiftUpdateCheck':'2660','LastUpgradeCheck':'2660','TargetAttributes':{v:{'CreatedOnToolsVersion':'26.6','TestTargetID':target_ids['MedicLogger']} if k.endswith('Tests') else {'CreatedOnToolsVersion':'26.6'} for k,v in target_ids.items()}},buildConfigurationList=config_list('project-config',{'CLANG_ENABLE_MODULES':'YES','ENABLE_TESTABILITY':'YES','SWIFT_VERSION':'5.0','SDKROOT':'iphoneos','IPHONEOS_DEPLOYMENT_TARGET':'17.0'}),compatibilityVersion='Xcode 15.0',developmentRegion='en',hasScannedForEncodings=0,knownRegions=['en','Base','fr','pt','zh-Hans'],mainGroup=main,productRefGroup=products,projectDirPath='',projectRoot='',targets=list(target_ids.values()))

def pbx(value, level=0):
    if isinstance(value,dict): return '{\n'+''.join('\t'*(level+1)+json.dumps(k)+ ' = '+pbx(v,level+1)+';\n' for k,v in value.items())+'\t'*level+'}'
    if isinstance(value,list): return '( '+', '.join(pbx(v,level) for v in value)+' )'
    if isinstance(value,int): return str(value)
    return json.dumps(value,ensure_ascii=False)
project=ROOT/'MedicLogger.xcodeproj';project.mkdir(exist_ok=True)
(project/'project.pbxproj').write_text('// !$*UTF8*$!\n'+pbx({'archiveVersion':1,'classes':{},'objectVersion':60,'objects':objects,'rootObject':project_id})+'\n')
scheme=project/'xcshareddata/xcschemes/MedicLogger.xcscheme';scheme.parent.mkdir(parents=True,exist_ok=True)
def build_ref(name): return f'<BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="{target_ids[name]}" BuildableName="{name}.{("app" if name=="MedicLogger" else "xctest")}" BlueprintName="{name}" ReferencedContainer="container:MedicLogger.xcodeproj"/>'
scheme.write_text(f'''<?xml version="1.0" encoding="UTF-8"?>
<Scheme LastUpgradeVersion="2660" version="1.3"><BuildAction parallelizeBuildables="YES" buildImplicitDependencies="YES"><BuildActionEntries><BuildActionEntry buildForTesting="YES" buildForRunning="YES" buildForProfiling="YES" buildForArchiving="YES" buildForAnalyzing="YES">{build_ref('MedicLogger')}</BuildActionEntry></BuildActionEntries></BuildAction><TestAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" shouldUseLaunchSchemeArgsEnv="YES"><Testables>{''.join('<TestableReference skipped="NO">'+build_ref(name)+'</TestableReference>' for name in ['MedicLoggerTests','MedicLoggerUITests'])}</Testables></TestAction><LaunchAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" launchStyle="0" useCustomWorkingDirectory="NO" ignoresPersistentStateOnLaunch="NO" debugDocumentVersioning="YES" debugServiceExtension="internal" allowLocationSimulation="YES"><BuildableProductRunnable runnableDebuggingMode="0">{build_ref('MedicLogger')}</BuildableProductRunnable></LaunchAction><ProfileAction buildConfiguration="Release" shouldUseLaunchSchemeArgsEnv="YES" savedToolIdentifier="" useCustomWorkingDirectory="NO" debugDocumentVersioning="YES"><BuildableProductRunnable runnableDebuggingMode="0">{build_ref('MedicLogger')}</BuildableProductRunnable></ProfileAction><AnalyzeAction buildConfiguration="Debug"/><ArchiveAction buildConfiguration="Release" revealArchiveInOrganizer="YES"/></Scheme>''')
print('Generated',project)
