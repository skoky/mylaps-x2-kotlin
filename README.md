
This is a **sample code** showing how to use [MyLaps X2 SDK](https://www.mylaps.com/x2-timing-data-system/x2-sdk) 
with [Kotlin language](https://kotlinlang.org/). The example binds SDK's native libs, creates SDK instance, 
calls its methods and handle events from X2 Appliance with excellent performance.
 
The [Kotlin Native](https://kotlinlang.org/docs/reference/native-overview.html) is used to bind 
local libs (not JVM version). Gradle is used to build the app with [Kotlin multi-platform](https://kotlinlang.org/docs/reference/building-mpp-with-gradle.html) 
plugin.

**MYLAPS is not responsible for this code and cannot provide support or warranty on it** If you ned any help, contact author of the repo.

# Supported platforms

The project can natively run on LinuxX64 or Windows X64 platforms. It also runs on MacOSX or others using Docker. There
is additional platform dependency on Intel X64.

Note: MyLaps provides X2 SDK binaries compiled for Intel 64bit and 32 it HW platforms only. 
Anyway, this example app support the 64 bit platform as this is commonly used today.   

# Pre-installation

- Contact [MyLaps](https://www.mylaps.com/x2-timing-data-system/x2-sdk) to download X2 SDK. Copy it to the root directory `sdk-master`, version min 4.1+
- Java/JDK 64 must be installed and present on PATH or JAVA_HOME
- install [ZeroMQ](http://zeromq.org/) to demonstrate SDK context handling 
- make sure you have X2 appliance available on network, have it hostname, username and password
- if dockerized version required, install Docker and Docker-compose

# Compiling example

## On Linux X64

The gradle build will download gradle building tool and all related dependencies.
    
Compiling example project using gradle:
    
    ./gradlew build

## On Windows X64

The gradle build will download gradle building tool and all related dependencies.
    
Compiling example project using gradle:
    
    gradlew.bat build

## On Mac OSX X64

Make sure Docker for Mac is install and run building process inside the docker container (using docker-compose)
See all details inside the `docker-compose.yml`:

    docker-compose run app-build

## (Optional) Compiling MyLaps SDK samples

Examples delivered with MyLaps SDK can be compiles using make. Because of dependencies, it's easier to run it inside
docker container on any platform like this:

    docker-compose run compile-sdk-samples


## An additional note to multi-platform compilation

Gradle can build the app for LinuxX64 on Mac and Windows platforms. Unfortunately it does not run on there and must 
be copied to a Linux box for testing. 

# Running example app on Linux

    export LD_LIBRARY_PATH=sdk-master/lib/linux/x86-64
    ./build/bin/linuxX64/debugExecutable/x2.kexe <hostname> <username> <password>

# Running example app on Windows

Copy all files from `sdk-master/lib/windows/x64` into the same directory where `x2.exe` is located.

    ./build/bin/mingwX64/debugExecutable/x2.exe <hostname> <username> <password>

# Running example app on Mac OSX

    docker-compose run x2 <x2hostname> <username> <password>

# Intellij IDEA support

If running Intellij Idea on Linux or Windows, it understands the Kotlin multi-platform project and its libraries (klib). 
This provides smart code completion and static type checking calling native code. This is very convenient and productive way of 
working with MyLaps X2 SDK in Kotlin. 

# MyLaps support

Thank you MyLaps for supporting this project!

# ZeroMQ usage

Usage of ZeroMQ demonstrates how MyLaps SDK passes context to custom handlers. The context is ZeroMQ connection/context used 
in handler to send `Passing` JSON to the ZMQ publisher queue. To listen on ZeroMQ messages, use a simple client `zmq/sub.py`

# Future work TBD

- covering more events from X2 appliance, like manual event, resend example, time sync etc
- more code sharing between Windows and Linux

# Known issues

- This issue is caused by Java 32bit platform used for compilation. Download and install 64bit Java/JDK version to fix

    > Task :cinteropMylapsLinuxX64 FAILED
    Error occurred during initialization of VM
    Could not reserve enough space for 3145728KB object heap
    
    > Task :cinteropMylapsMingwX64 FAILED
    Error occurred during initialization of VM
    Could not reserve enough space for 3145728KB object heap

- `strcpy` issue

If running into the issue with strcpy, there is workaround to fix this:

    Exception in thread "main" java.lang.Error: C:\Users\adams\.konan\dependencies\msys2-mingw-w64-x86_64-gcc-7.3.0-clang-llvm-lld-6.0.1\x86_64-w64-mingw32\include\sec_api/string_s.h:39:27: error: conflicting types for 'strncpy'

Edit `sdk-master/include/TTPlatform.h` and comment out all additional functions definition in section "Defines to make gcc compatible with Visual Studio 2005"