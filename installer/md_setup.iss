[Setup]
AppName=MDRenderer
AppVersion=1.0.10
AppPublisher=MDRenderer
PrivilegesRequired=lowest
DefaultDirName={autopf}\MDRenderer
DefaultGroupName=MDRenderer
UninstallDisplayIcon={app}\icon.ico
OutputDir=C:\wpjc\md\installer
OutputBaseFilename=MDRenderer-Setup
Compression=lzma2
SolidCompression=yes
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
; 变更时通知用户关闭程序
CloseApplications=yes

[Files]
; Python/Cython 前端 + GraalVM DLL + Python 运行时
Source: "C:\wpjc\md\pyfrontend\*"; DestDir: "{app}"; Flags: recursesubdirs createallsubdirs ignoreversion

[Icons]
Name: "{group}\MDRenderer"; Filename: "{app}\MDRenderer.bat"; IconFilename: "{app}\icon.ico"
Name: "{group}\卸载 MDRenderer"; Filename: "{uninstallexe}"
Name: "{autodesktop}\MDRenderer"; Filename: "{app}\MDRenderer.bat"; IconFilename: "{app}\icon.ico"; Tasks: desktopicon

[Tasks]
Name: desktopicon; Description: "创建桌面快捷方式"; GroupDescription: "附加图标:"
Name: associate; Description: "关联 .md 文件到 MDRenderer"; GroupDescription: "文件关联:"; Flags: unchecked

[Registry]
; .md 文件关联（仅当用户勾选 associate 任务时）
Root: HKCR; Subkey: ".md"; ValueType: string; ValueName: ""; ValueData: "MDRenderer.md"; Flags: uninsdeletevalue; Tasks: associate
Root: HKCR; Subkey: "MDRenderer.md"; ValueType: string; ValueName: ""; ValueData: "Markdown 文档"; Flags: uninsdeletekey; Tasks: associate
Root: HKCR; Subkey: "MDRenderer.md\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\icon.ico,0"; Flags: uninsdeletekey; Tasks: associate
Root: HKCR; Subkey: "MDRenderer.md\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\MDRenderer.bat"" ""%1"""; Flags: uninsdeletekey; Tasks: associate

[Run]
Filename: "{app}\MDRenderer.bat"; Description: "运行 MDRenderer"; Flags: nowait postinstall skipifsilent

[UninstallDelete]
; 卸载时清理用户数据目录
Type: filesandordirs; Name: "{userappdata}\.mdrenderer"
