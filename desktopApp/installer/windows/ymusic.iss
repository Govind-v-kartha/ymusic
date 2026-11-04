; Inno Setup script for YMusic, compiled as-is by the Gradle task `packageReleaseInnoSetup`, which
; passes the version, paths and arch as ISCC /D defines.
;
; Build input (jpackage's `createReleaseDistributable` output):
;   AppSourceDir = desktopApp/build/compose/binaries/main-release/app/YMusic
;     containing YMusic.exe + runtime/ + app/
;
; Output:
;   OutputDir/OutputBaseFilename.exe (self-contained installer, LZMA2)

#define AppName "YMusic"
#define AppExeName "YMusic.exe"
; Never change: Inno finds the previous install by AppId, so a new one would install side by side.
#define AppId "{{B83F7249-16C3-4E8A-9A2E-8E5F7C0D9B12}"

[Setup]
AppId={#AppId}
AppName={#AppName}
AppVersion={#AppVersion}
AppVerName={#AppName} {#AppVersion}
AppPublisher={#AppPublisher}
AppPublisherURL={#AppURL}
AppSupportURL={#AppSupportURL}
AppUpdatesURL={#AppURL}
DefaultDirName={autopf}\{#AppName}
DefaultGroupName={#AppName}
; auto = show the chooser on fresh install (user picks where to go), hide it on upgrade
; (same path as the detected previous install — swapping it mid-upgrade breaks tracking).
DisableProgramGroupPage=auto
DisableDirPage=auto
AllowNoIcons=yes
OutputDir={#OutputDir}
OutputBaseFilename={#OutputBaseName}
SetupIconFile={#IconFile}
UninstallDisplayIcon={app}\{#AppExeName}
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern
; Per-machine install into Program Files, requires UAC — same as the previous MSI. Switch to
; `lowest` and `DefaultDirName={localappdata}\Programs\{#AppName}` for a Chrome-style per-user
; install that skips the elevation prompt.
PrivilegesRequired=admin
; Arch: x64compatible, or arm64 for the native Windows on ARM build.
ArchitecturesAllowed={#Arch}
ArchitecturesInstallIn64BitMode={#Arch}
MinVersion=10.0
CloseApplications=force
RestartApplications=no
ChangesAssociations=yes

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked
Name: "associatefiles"; Description: "Register in 'Open with' for common audio formats (.mp3, .m4a, .flac, .opus, .ogg, .wav)"; Flags: unchecked

; An upgrade replaces the app image wholesale: jars and DLLs the new version dropped must not linger.
[InstallDelete]
Type: filesandordirs; Name: "{app}\app"
Type: filesandordirs; Name: "{app}\runtime"

[Files]
Source: "{#AppSourceDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\{#AppName}"; Filename: "{app}\{#AppExeName}"
Name: "{autodesktop}\{#AppName}"; Filename: "{app}\{#AppExeName}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#AppExeName}"; Description: "{cm:LaunchProgram,{#StringChange(AppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent

[Registry]
Root: HKA; Subkey: "Software\Classes\.mp3\OpenWithProgids"; ValueType: string; ValueName: "YMusic.Audio"; ValueData: ""; Flags: uninsdeletevalue; Tasks: associatefiles
Root: HKA; Subkey: "Software\Classes\.m4a\OpenWithProgids"; ValueType: string; ValueName: "YMusic.Audio"; ValueData: ""; Flags: uninsdeletevalue; Tasks: associatefiles
Root: HKA; Subkey: "Software\Classes\.flac\OpenWithProgids"; ValueType: string; ValueName: "YMusic.Audio"; ValueData: ""; Flags: uninsdeletevalue; Tasks: associatefiles
Root: HKA; Subkey: "Software\Classes\.opus\OpenWithProgids"; ValueType: string; ValueName: "YMusic.Audio"; ValueData: ""; Flags: uninsdeletevalue; Tasks: associatefiles
Root: HKA; Subkey: "Software\Classes\.ogg\OpenWithProgids"; ValueType: string; ValueName: "YMusic.Audio"; ValueData: ""; Flags: uninsdeletevalue; Tasks: associatefiles
Root: HKA; Subkey: "Software\Classes\.wav\OpenWithProgids"; ValueType: string; ValueName: "YMusic.Audio"; ValueData: ""; Flags: uninsdeletevalue; Tasks: associatefiles
Root: HKA; Subkey: "Software\Classes\YMusic.Audio"; ValueType: string; ValueName: ""; ValueData: "YMusic audio file"; Flags: uninsdeletekey; Tasks: associatefiles
Root: HKA; Subkey: "Software\Classes\YMusic.Audio\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#AppExeName},0"; Tasks: associatefiles
Root: HKA; Subkey: "Software\Classes\YMusic.Audio\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#AppExeName}"" ""%1"""; Tasks: associatefiles
