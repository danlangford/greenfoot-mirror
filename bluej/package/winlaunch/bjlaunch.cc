/*
 This file is part of the BlueJ program. 
 Copyright (C) 1999-2009  Michael Kolling and John Rosenberg 
 
 This program is free software; you can redistribute it and/or 
 modify it under the terms of the GNU General Public License 
 as published by the Free Software Foundation; either version 2 
 of the License, or (at your option) any later version. 
 
 This program is distributed in the hope that it will be useful, 
 but WITHOUT ANY WARRANTY; without even the implied warranty of 
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the 
 GNU General Public License for more details. 
 
 You should have received a copy of the GNU General Public License 
 along with this program; if not, write to the Free Software 
 Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA. 
 
 This file is subject to the Classpath exception as provided in the  
 LICENSE.txt file that accompanied this code.
 */
#define UNICODE

#include <windows.h>
#include "resources.h"
#include <set>
#include <string>
#include <cstring>
#include <list>


#undef __cplusplus
#include <jni.h>
#define __cplusplus

#ifndef APPNAME
#define APPNAME "BlueJ"
#endif

#ifndef REQUIREDJAVA
#define REQUIREDJAVA "1.5"
#endif


#include <cstdio>

// Handy typedef
typedef std::basic_string<TCHAR> string;

// This define must match that in bjdialog.cc
#define MSG_LAUNCHVM WM_APP

// Methods from javatest.cc
bool testJdkPath(string jdkLocation, string *reason);

// Methods from javaprops.cc
string readJavaProperty(string file, std::string propertyName);
string getBlueJProperty(std::string propertyName);

// Methods from bjdialog.cc
INT_PTR CALLBACK MainDialogProc (HWND hwnd, 
                          UINT message, 
                          WPARAM wParam, 
                          LPARAM lParam);


// GLOBAL VARIABLES


// Application instance
HINSTANCE appInstance = 0;

// The set of VMs which we have found
std::set<string> goodVMs;

// The version number, set in WinMain
string appVersion;

// The path to BlueJ (where the launcher is located). Set in WinMain.
string bluejPath;

// The path to User's home directory (java.home). Guessed in WinMain.
LPTSTR userHomePath;

// List of arguments to be passed to BlueJ's main method
std::list<LPCWSTR> bjargs;

// List of arguments for the main VM
std::list<LPCWSTR> windowsvmargs;

// Whether we should always launch java as an external process
bool externalLaunch = false;



// Get a registry value. The result should be delete[]'d to free it.
// Returns NULL if there is an error or the result is not a string.
static LPTSTR getRegistryValue(HKEY key, LPCTSTR valueName)
{
	DWORD dataSize = 0;
	DWORD dataType;
	LPTSTR result = NULL;
	
	LONG rval = RegQueryValueEx(key, valueName, NULL, &dataType, NULL, &dataSize);
	if (rval == ERROR_SUCCESS && dataType == REG_SZ) {
		do {
			int stringSize = (dataSize + sizeof(TCHAR) - 1) / sizeof(TCHAR) + 1;
			result = new TCHAR[stringSize];
			rval = RegQueryValueEx(key, valueName, NULL, &dataType, (BYTE *) result, &dataSize);
			if (rval != ERROR_SUCCESS || dataType != REG_SZ) {
				delete [] result;
				result = NULL;
			}
		} while(rval == ERROR_MORE_DATA);
	}
	
	return result;
}


// Check the registry to see if a VM was selected previously.
// If there is a selected VM, it is checked for validity and added
// to the goodVMs set if valid.
static void checkCurrentVM()
{
	string bluejRegKey = TEXT("Software\\" APPNAME "\\" APPNAME "\\");
	bluejRegKey += appVersion;

 	// Check for a Current VM in the registry
	HKEY regKey;
	LONG rval = RegOpenKeyEx(HKEY_CURRENT_USER, bluejRegKey.c_str(), 0, KEY_READ, &regKey);
	if (rval != ERROR_SUCCESS) {
		// Try HKEY_LOCAL_MACHINE as well
		rval = RegOpenKeyEx(HKEY_LOCAL_MACHINE, bluejRegKey.c_str(), 0, KEY_READ, &regKey);
	}

	if (rval == ERROR_SUCCESS) {
		LPTSTR jdkLocation = getRegistryValue(regKey, TEXT("CurrentVM"));
		if (jdkLocation != NULL) {
			if (testJdkPath(jdkLocation, NULL)) {
				goodVMs.insert(string(jdkLocation));
			}
		}
		RegCloseKey(regKey);
	}
}

// convert a UTF16 string to a UTF8 string.
// Result should be delete[]'d when done with.
static char * wideToUTF8(std::basic_string<WCHAR> s)
{
	// Assume max of 4 UTF8 bytes to represent a WCHAR
	int inputChars = s.length();
	int outputUTF8size = inputChars * 4 + 1;
	
	char *outputUTF8 = new char[outputUTF8size];
	WideCharToMultiByte(CP_UTF8, 0, s.c_str(), inputChars + 1,
			outputUTF8, outputUTF8size, NULL, NULL);
	
	return outputUTF8;
}


// Escape (quote) a command line parameter if necessary.
// Windows uses a really stupid escaping system - any number of backslashes not followed
// by a quote are not escapes.
string escapeCmdlineParam(string param)
{
	bool hasSpace = param.find(TEXT(' ')) != string::npos;
	
	if (hasSpace || param.find(TEXT('\"')) != string::npos) {
		// Contains a space
		string output;
		if (hasSpace) {
			output = TEXT("\"");
		}
		unsigned bsCount = 0;
		for (string::iterator i = param.begin(); i != param.end(); i++) {
			TCHAR c = *i;
			if (c == TEXT('\\')) {
				bsCount++;
			}
			else if (c == TEXT('\"')) {
				// Possibly a number of backslashes before a quote.
				// We need to double the number of backslashes:
				for (unsigned j = 0; j < bsCount; j++) {
					output += TEXT('\\');
				}
				bsCount = 0;
			}
			else {
				bsCount = 0;
			}
			output += c;
		}
		if (hasSpace) {
			output += TEXT('\"');
		}
		return output;
	}
	else {
		return param;
	}
}


// Save selected JDK location to the registry.
void saveSelectedJdk(LPCTSTR jdkLocation)
{
	string bluejRegKey = TEXT("Software\\" APPNAME "\\" APPNAME "\\");
	bluejRegKey += appVersion;

 	// Check for a Current VM in the registry
	HKEY regKey;
	LONG rval = RegCreateKeyEx(HKEY_CURRENT_USER, bluejRegKey.c_str(), 0, NULL, REG_OPTION_NON_VOLATILE,
			KEY_SET_VALUE, NULL, &regKey, NULL);

	if (rval == ERROR_SUCCESS) {
		rval = RegSetValueEx(regKey, TEXT("CurrentVM"), 0, REG_SZ, (const BYTE *) jdkLocation,
				(lstrlen(jdkLocation) + 1) * sizeof(TCHAR));
				
		RegCloseKey(regKey);
	}
	
}



// Launch VM as an external process
// Returns - true if successful
bool launchVMexternal(string jdkLocation)
{
	string javaCommand = jdkLocation + TEXT("\\bin\\javaw.exe");
	
	string commandLine = escapeCmdlineParam(javaCommand) + TEXT(' ');
	
	bool firstArg = true;
    for (std::list<LPCTSTR>::iterator i = windowsvmargs.begin(); i != windowsvmargs.end(); ++i) {
		if (! firstArg) {
			commandLine += ' ';
		}
		firstArg = false;
		commandLine += escapeCmdlineParam(*i);
	}
	
	if (! firstArg) {
		commandLine += TEXT(' ');
	}
	
	commandLine += TEXT("-classpath ");
	string classPathString = bluejPath + TEXT("\\lib\\bluej.jar;");
	classPathString += jdkLocation + TEXT("\\lib\\tools.jar");
	commandLine += escapeCmdlineParam(classPathString);
	
	commandLine += TEXT(" bluej.Boot");
		
	for (std::list<LPCTSTR>::iterator i = bjargs.begin(); i != bjargs.end(); ++i) {
		commandLine += TEXT(' ');
		commandLine += escapeCmdlineParam(*i);
	}
	
	PROCESS_INFORMATION processInfo;
	STARTUPINFO startupInfo;
	
	ZeroMemory(&processInfo, sizeof(processInfo));
	ZeroMemory(&startupInfo, sizeof(startupInfo));
	startupInfo.cb = sizeof(startupInfo);
	
	LPTSTR cmdLineCopy = new TCHAR[commandLine.length() + 1];
	lstrcpy(cmdLineCopy, commandLine.c_str());
	
	DWORD error = ERROR_SUCCESS;
	if (!CreateProcess(javaCommand.c_str(), cmdLineCopy, NULL, NULL, TRUE, 0, NULL,
			bluejPath.c_str(), &startupInfo, &processInfo)) {
		// Error - try java.exe instead of javaw.exe
		error = GetLastError();
		lstrcpy(cmdLineCopy, commandLine.c_str());
		javaCommand = jdkLocation + TEXT("\\bin\\java.exe");
		
		if (!CreateProcess(javaCommand.c_str(), cmdLineCopy, NULL, NULL, TRUE, 0, NULL,
				bluejPath.c_str(), &startupInfo, &processInfo)) {
			if (error == ERROR_FILE_NOT_FOUND) {
				// javaw.exe wasn't found, but that's not a useful error message...
				error = GetLastError();
			}
			
			TCHAR buf[1024];
			wsprintf (buf, TEXT(APPNAME " could not launch Java: Error h\'%X"), GetLastError ());
			MessageBox(0, buf, TEXT(APPNAME), MB_ICONEXCLAMATION | MB_OK);
			return false;
		}
	}

	saveSelectedJdk(jdkLocation.c_str());
	return true;
}




// Launch VM and run bluej.Boot class
//   jdkLocation - location of the JDK (no trailing slash!)
// Returns - false on failure
//       Note, if this call succeeds, it might not return at all.
bool launchVM(string jdkLocation)
{
	if (externalLaunch || ! windowsvmargs.empty()) {
		// If there are VM arguments, do an external launch. It is too difficult
		// to translate java.exe arguments to javavm.dll options.
		return launchVMexternal(jdkLocation);
	}

	typedef typeof(JNI_CreateJavaVM) *JNI_CreateJavaVM_t;
	//typedef typeof(JNI_GetDefaultJavaVMInitArgs) *JNI_GetDefaultJavaVMInitArgs_t;
	
	JavaVM *javaVM;
	JNIEnv *jniEnv;
	
	// Try and load msvcr71.dll from the JDK directory first. Otherwise loading the jvm seems
	// to fail on some machines.
	HINSTANCE hMsvcrlib;
	hMsvcrlib = LoadLibrary( (jdkLocation + TEXT("\\jre\\bin\\msvcr71.dll")).c_str() );
	
	// Now load the JVM.
	HINSTANCE hJavalib;
	hJavalib = LoadLibrary( (jdkLocation + TEXT("\\jre\\bin\\client\\jvm.dll")).c_str() );
	if (hJavalib == NULL) {
		return launchVMexternal(jdkLocation);
	}
	
	// We've loaded a VM, but if we bail out later, let's make sure we don't load
	// another VM and have two loaded at once. That might cause problems.
	externalLaunch = true;
	
	// Make real classpath string. Needs to be UTF-8?
	
	char * bjDirUTF8 = wideToUTF8(bluejPath);
	char * jdkLocUTF8 = wideToUTF8(jdkLocation);
	
	std::string classPathOpt = "-Djava.class.path=";
	(classPathOpt += bjDirUTF8) += "\\lib\\bluej.jar;";
	(classPathOpt += jdkLocUTF8) += "\\lib\\tools.jar";
	
	delete [] bjDirUTF8;
	delete [] jdkLocUTF8;
	
	char *classPathOptArr = new char[classPathOpt.length() + 1];
	strcpy(classPathOptArr, classPathOpt.c_str());
	
	JavaVMInitArgs vm_args;
    
	JavaVMOption * options = new JavaVMOption[1 + windowsvmargs.size()];
	//JavaVMOption options[1];
	options[0].optionString = classPathOptArr;
	options[0].extraInfo = NULL;
	int j = 1;
    for (std::list<LPCTSTR>::iterator i = windowsvmargs.begin(); i != windowsvmargs.end(); ) {
		options[j].optionString = wideToUTF8(string(*i));
		options[j].extraInfo = NULL;
		j++; i++;
	}
	
	vm_args.version = 0x00010002;
    vm_args.options = options;
    vm_args.nOptions = 1;
	vm_args.ignoreUnrecognized = JNI_TRUE;
	
	JNI_CreateJavaVM_t CreateJavaVM_ptr = (JNI_CreateJavaVM_t) GetProcAddress( hJavalib, "JNI_CreateJavaVM" );
	if (CreateJavaVM_ptr == NULL) {
		for (j = 0; j <= windowsvmargs.size(); j++) {
			delete [] options[j].optionString;
		}
		delete [] options;
		return launchVMexternal(jdkLocation);
	}
			
	int res = CreateJavaVM_ptr(&javaVM, (void **)&jniEnv, &vm_args);

	for (j = 0; j <= windowsvmargs.size(); j++) {
		delete [] options[j].optionString;
	}
	delete [] options;
	
	if (res < 0 || jniEnv == NULL) {
		return launchVMexternal(jdkLocation);
	}
	
	jclass cls = (*jniEnv)->FindClass(jniEnv, "bluej/Boot");
	if (cls == NULL) {
		MessageBox (0, TEXT("Couldn't find bluej.Boot class"), TEXT("BlueJ launcher"), MB_ICONEXCLAMATION | MB_OK);
		goto destroyvm;
	}
	
	jmethodID mid = (*jniEnv)->GetStaticMethodID(jniEnv, cls, "main", "([Ljava/lang/String;)V");
	if (mid == NULL) {
		MessageBox (0, TEXT("Couldn't find main() method in bluej.Boot class"), TEXT("BlueJ launcher"), MB_ICONEXCLAMATION | MB_OK);
		goto destroyvm;
	}
	
	// Create the String[] argument for main
	jclass stringClass = (*jniEnv)->FindClass(jniEnv, "java/lang/String");
    jobjectArray args = (*jniEnv)->NewObjectArray(jniEnv, bjargs.size(), stringClass, NULL);
	
	{
		int j = 0;
		for (std::list<LPCTSTR>::iterator i = bjargs.begin(); i != bjargs.end();) {
			jstring argString = (*jniEnv)->NewString(jniEnv, (const jchar *) *i, lstrlen(*i));
			(*jniEnv)->SetObjectArrayElement(jniEnv, args, j, argString);
			++j; ++i;
		}
	}
	
	
	// Must save the selected JDK first. Once we've let Java take over there doesn't
	// seem to be any guarentee that further code in this program will be executed.
	saveSelectedJdk(jdkLocation.c_str());
	(*jniEnv)->CallStaticVoidMethod(jniEnv, cls, mid, args);
	
	if ((*jniEnv)->ExceptionOccurred(jniEnv)) {
		// if an exception occurs, output to stderr (which is usually invisible, oh well...)
         (*jniEnv)->ExceptionDescribe(jniEnv);
    }
    
    // Run a windows message loop. This is a simple way to sleep while BlueJ runs.
	MSG  msg;
    int status;
    while ((status = GetMessage (& msg, 0, 0, 0)) != 0)
    {
        if (status == -1) {
            return true; // shouldn't happen anyway
		}

		TranslateMessage ( & msg );
		DispatchMessage ( & msg );
    }
	
	destroyvm:
	(*javaVM)->DestroyJavaVM(javaVM);
	return true;
}



// Find VMs under a VM provider key eg (sun, ibm)
static void findRegistryVMs(string providerKey)
{
	HKEY regKey;
	LONG rval = RegOpenKeyEx(HKEY_LOCAL_MACHINE, providerKey.c_str(), 0, KEY_READ, &regKey);
	
	TCHAR buffer[1024];
	
	if (rval == ERROR_SUCCESS) {
		DWORD dwIndex = 0;
		do {
			DWORD bufSize = 1024;
			rval = RegEnumKeyEx(regKey, dwIndex, buffer, &bufSize, NULL, NULL, NULL, NULL);
			if (rval == ERROR_SUCCESS) {
				HKEY subKey;
				LONG vval = RegOpenKeyEx(regKey, buffer, 0, KEY_QUERY_VALUE, &subKey);
				if (vval == ERROR_SUCCESS) {
					if (lstrcmp(buffer, TEXT(REQUIREDJAVA)) > 0) {
						// Ok - suitable version
						LPTSTR jdkLocation = getRegistryValue(subKey, TEXT("JavaHome"));
						if (jdkLocation != NULL) {
							if (testJdkPath(jdkLocation, NULL)) {
								goodVMs.insert(string(jdkLocation));
							}
						}
					}
				}
			}
			dwIndex++;
		} while (rval != ERROR_NO_MORE_ITEMS);
	}
}

// Find VMs from the registry
static void findRegistryVMs()
{
	findRegistryVMs(TEXT("Software\\JavaSoft\\Java Development Kit"));
	findRegistryVMs(TEXT("Software\\IBM\\Java Development Kit"));
}


// Extract path from file
string extractFilePath(string filename)
{
	int bspos = filename.rfind('\\');
	if (bspos == string::npos) {
		return string();
	}
	else {
		return filename.substr(0, bspos);
	}
}


// Program entry point
int WINAPI WinMain
   (HINSTANCE hInst, HINSTANCE hPrevInst, char * cmdParam, int cmdShow)
{
    appInstance = hInst;
	bool forceVMselect = false; // whether we MUST show window
	
	LPTSTR commandLine = GetCommandLine();
	
	int argCount = 0;
	LPWSTR *args = CommandLineToArgvW(commandLine, &argCount);

	// args[0] = executable path. However, despite misleading MS
	// documentation, it is not necessarily a fully qualified path
	// (it might be a relative path).
	
	LPTSTR bluejPathBuffer = new TCHAR[MAX_PATH];
	if (!GetFullPathName(args[0], MAX_PATH, bluejPathBuffer, NULL)) {
		MessageBox(0, TEXT("Couldn't get path to launcher executable"), TEXT(APPNAME), MB_ICONERROR | MB_OK);
		return 1;
	}
	bluejPath = extractFilePath(bluejPathBuffer);
	delete [] bluejPathBuffer;

	// Check parameters
	for (int i = 1; i < argCount; i++) {
		if (lstrcmpi(TEXT("/select"), args[i]) == 0) {
			forceVMselect = true;
		}
		else if (lstrcmpi(TEXT("/javaw"), args[i]) == 0) {
			// ignore for backwards compatibility
		}
		else if (lstrcmpi(TEXT("/externalvm"), args[i]) == 0) {
			// force external launch of VM (as a separate process)
			externalLaunch = true;
		}
		else {
			bjargs.push_back(args[i]);
		}
	}
	
#ifdef GREENFOOT
	bjargs.push_back(TEXT("-greenfoot=true"));
	bjargs.push_back(TEXT("-bluej.compiler.showunchecked=false"));
#endif
	
	// Get application version string from version resource
	HRSRC hRsrc = FindResource(NULL, MAKEINTRESOURCE(1), RT_VERSION);
	if (hRsrc != NULL) {
		HGLOBAL hGlbl = LoadResource(NULL, hRsrc);
		if (hGlbl != NULL) {
			BYTE *verBuffer = (BYTE *) LockResource(hGlbl);
			if (verBuffer != NULL) {
				UINT plen = 0;
				LPTSTR productVersion = 0;
				BOOL verResult = VerQueryValue(verBuffer,
						TEXT("\\StringFileInfo\\04091200\\ProductVersion"),
						(void **) &productVersion, &plen);
				appVersion = productVersion;
				
			}
			else {
				hRsrc = NULL;
			}
		}
		else {
			hRsrc = NULL;
		}
	}

	// Locate user home directory
	{
		string userHomeString = getBlueJProperty("bluej.userHome");
		if (! userHomeString.empty()) {
			userHomePath = new TCHAR[userHomeString.length() + 1];
			lstrcpy(userHomePath, userHomeString.c_str());
		}
		else {
			DWORD envHomeSize = GetEnvironmentVariable(TEXT("HOME"), NULL, 0);
			if (envHomeSize != 0) {
				userHomePath = new TCHAR[envHomeSize];
				GetEnvironmentVariable(TEXT("HOME"), userHomePath, envHomeSize);
			}
		}
	}

	// Get bluej.windows.vm.args
	{
		string windowsvmargsString = getBlueJProperty("bluej.windows.vm.args");
		if (! windowsvmargsString.empty()) {
			int argCount = 0;
			// TODO it's technically wrong to use CommandLineToArgvW, as the
			// escaping technique it supports is slightly different.
			LPWSTR *args = CommandLineToArgvW(windowsvmargsString.c_str(), &argCount);
			for (int i = 0; i < argCount; i++) {
				windowsvmargs.push_back(args[i]);
			}
		}
	}

  	// Check to see if there's a currently selected VM
	checkCurrentVM();
	
	if (!forceVMselect && !goodVMs.empty()) {
		const string &jdkLocation = *(goodVMs.begin());
		if (launchVM(jdkLocation)) {
			return 0;
		}
	}
	
	
	// Check for VM in bluej.defs
	string defsVm = getBlueJProperty("bluej.windows.vm");
	if (defsVm.length() != 0) {
		string reason;
		if (testJdkPath(defsVm, &reason)) {
			if (! forceVMselect) {
				if (launchVM(defsVm)) {
					return 0;
				}
			}
			goodVMs.insert(defsVm);
		}
	}
	
	// Locate other VMs
	findRegistryVMs();
	if (!forceVMselect && goodVMs.size() == 1) {
		const string &jdkLocation = *(goodVMs.begin());
		if (launchVM(jdkLocation)) {
			return 0;
		}
	}
		
	
	HWND hDialog = 0;

    hDialog = CreateDialog (hInst, 
                            MAKEINTRESOURCE (DLG_MAIN), 
                            0, 
                            MainDialogProc);

    if (!hDialog)
    {
        TCHAR buf [100];
        wsprintf (buf, TEXT(APPNAME " launcher could not create dialog: Error h\'%X"), GetLastError ());
        MessageBox (0, buf, TEXT(APPNAME), MB_ICONEXCLAMATION | MB_OK);
        return 1;
    }
	
	
		
	// Display the window
	ShowWindow(hDialog, SW_SHOWNORMAL);
	
	if (goodVMs.empty()) {
        MessageBox (0, TEXT("No (suitable) Java JDKs were found. " APPNAME " requires JDK version " REQUIREDJAVA " or later.\n"
					"Please also note, the Java Runtime Environment (JRE) is not sufficient.\n"
					"You must have a JDK to run " APPNAME ".\n\n"
					"The launcher will continue to run - if you have a JDK installed, "
					"you can browse\nfor it (use the browser button)."),
				TEXT(APPNAME), MB_ICONEXCLAMATION | MB_OK);
	}


    MSG  msg;
    int status;
    while ((status = GetMessage (& msg, 0, 0, 0)) != 0)
    {
        if (status == -1) {
            return -1;
		}
			
        if (msg.message == MSG_LAUNCHVM) {
			DestroyWindow(hDialog);
			string jdkLocation = ((TCHAR *) msg.lParam);
			if (launchVM(jdkLocation)) {
				return 0;
			}
		}

        if (!IsDialogMessage (hDialog, & msg))
        {
			TranslateMessage ( & msg );
			DispatchMessage ( & msg );
        }
    }

	// We've received WM_QUIT
    return msg.wParam;
}
